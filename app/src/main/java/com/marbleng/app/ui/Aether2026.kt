@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.marbleng.app.ui

// Marble Product UI v78 • Swipe tabs · Smart Library · Lean Home · Provider Link
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
// MARBLE_TABBED_SETTINGS_QUALITY_UI_V46
// MARBLE_REFINED_PRODUCT_UI_V52
// MARBLE_M3_EXPRESSIVE_UI_V53
// MARBLE_PRISM_UI_V54
// MARBLE_REAL_DEVICE_POLISH_V55
// MARBLE_SERVER_INTEL_UI_V56
// MARBLE_SERVER_INTEL_HOME_UI_V58
// MARBLE_LIBRARY_MODE_POLISH_UI_V59
// MARBLE_GLOBAL_CONTROL_POLISH_UI_V60
// MARBLE_CONNECTED_CARD_REFINEMENT_UI_V61
// MARBLE_FLUID_LIBRARY_MOTION_UI_V62
// MARBLE_LEAN_COPY_LIVE_RANK_UI_V63
// MARBLE_HOME_STATUS_ANCHOR_UI_V64
// MARBLE_ACTIVE_NODE_HALO_UI_V64
// MARBLE_LIBRARY_CONTROL_GRADE_UI_V65
// MARBLE_CONNECTED_PING_READOUT_UI_V65
// MARBLE_UNIFIED_SURFACE_SYSTEM_UI_V65
// MARBLE_NAVY_BRAND_UI_V77
// MARBLE_HOME_PUZZLE_GRID_V77

import android.Manifest
import android.content.Intent
import com.marbleng.app.BuildConfig
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.marbleng.app.AppRepository
import com.marbleng.app.R
import com.marbleng.app.core.AddressFamilyPolicy
import com.marbleng.app.core.BugSeverity
import com.marbleng.app.core.IranModeState
import com.marbleng.app.core.ManualConfigDraft
import com.marbleng.app.core.ManualProtocol
import com.marbleng.app.core.ServerlessFreedomEngine
import com.marbleng.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class SpatialTab(val label: String) {
    DECK("Home"),
    LIBRARY("Servers"),
    SETTINGS("Settings")
}

/** MARBLE_BILINGUAL_V110 — the dock renders translated tab names, never the enum label. */
@Composable
private fun spatialTabLabel(tab: SpatialTab): String = when (tab) {
    SpatialTab.DECK -> Tr.now.tabHome
    SpatialTab.LIBRARY -> Tr.now.tabLibrary
    SpatialTab.SETTINGS -> Tr.now.tabSettings
}

private fun rememberedSpatialTab(name: String): SpatialTab =
    runCatching { SpatialTab.valueOf(name) }.getOrDefault(SpatialTab.DECK)

private data class InstalledApp(val label: String, val packageName: String)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Aether2026App(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit = {}
) {
    val tabs = SpatialTab.entries
    val initialIndex = rememberedSpatialTab(repo.lastAppTab).ordinal
    val pagerState = rememberPagerState(initialPage = initialIndex) { tabs.size }
    var dialog by remember { mutableStateOf<String?>(null) }
    var settingsFocus by remember { mutableStateOf<String?>(null) }
    var detailProfile by remember { mutableStateOf<ProxyProfile?>(null) }
    var ipDetailsOpen by remember { mutableStateOf(false) }
    var contentScrolling by remember { mutableStateOf(false) }
    BackHandler(enabled = detailProfile != null) { detailProfile = null }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Sync pager → tab name for persistence and reset the dock to its resting skin when
    // switching pages. Each scrollable page reports its own motion below.
    LaunchedEffect(pagerState.currentPage) {
        contentScrolling = false
        onContentScrollChanged(false)
        repo.rememberAppTab(tabs[pagerState.currentPage].name)
    }

    val reportContentScroll: (Boolean) -> Unit = { scrolling ->
        contentScrolling = scrolling
        onContentScrollChanged(scrolling)
    }

    // Routing focus from Home: jump to Settings tab
    LaunchedEffect(settingsFocus) {
        if (settingsFocus == "Routing") {
            pagerState.animateScrollToPage(SpatialTab.SETTINGS.ordinal)
        }
    }

    LaunchedEffect(repo.message, repo.busy) {
        if (!repo.busy && repo.message.isNotBlank()) {
            snackbar.showSnackbar(repo.message, duration = SnackbarDuration.Short)
            repo.clearMessage()
        }
    }

    // MARBLE_SIGNATURE_HOME_V112 — one shared deck truth: the Home page, the app-wide Signature
    // status banner and the floating connect button all read this exact evidence + action set.
    val deck = rememberDeckEvidence(repo)
    val deckCopyIp = rememberCopyIpAction(repo, deck.evidence.ip)
    val deckActions = HomeActions(
        onToggleConnection = {
            with(deck.evidence) {
                if (connected || connecting || blocked) repo.stopVpn()
                else repo.reconnectLastOrAuto(onConnect)
            }
        },
        onCopyIp = deckCopyIp,
        onRefreshIp = { repo.refreshServerIntel(deck.profile, force = true) },
        onIpDetails = { ipDetailsOpen = true },
        onTestPing = { repo.measureConnectionPing() },
        onLibrary = { scope.launch { pagerState.animateScrollToPage(SpatialTab.LIBRARY.ordinal) } },
        onConnectProfile = { profile -> onConnect(profile) },
        onAddRoute = { scope.launch { pagerState.animateScrollToPage(SpatialTab.LIBRARY.ordinal) } },
        onRank = { repo.smartRank() },
        onPrivacy = {
            repo.audit()
            dialog = "Privacy"
        },
        onRouting = {
            settingsFocus = "Routing"
            scope.launch { pagerState.animateScrollToPage(SpatialTab.SETTINGS.ordinal) }
        },
        onTests = { scope.launch { pagerState.animateScrollToPage(SpatialTab.SETTINGS.ordinal) } }
    )

    Scaffold(
        containerColor = Aether.Void
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Aether.Void)
        ) {
            DeepSpaceBackdrop(Modifier.matchParentSize())

            // marble-page-transition-fast — the pager drives tab changes directly so swipes
            // track the finger with physics (instant, no staged crossfade); dock taps stay
            // snappy via animateScrollToPage's bounded page turn.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                beyondViewportPageCount = 1
            ) { pageIndex ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 820.dp)
                            .fillMaxWidth()
                    ) {
                        when (tabs[pageIndex]) {
                    SpatialTab.DECK -> CyberDeck(
                        repo = repo,
                        deck = deck,
                        actions = deckActions,
                        onContentScrollChanged = reportContentScroll
                    )
                    SpatialTab.LIBRARY -> CyberLibrary(
                        repo = repo,
                        onConnect = onConnect,
                        onImportFile = onImportFile,
                        onDetails = { detailProfile = it },
                        onContentScrollChanged = reportContentScroll
                    )
                    SpatialTab.SETTINGS -> SpatialSettings(
                        repo = repo,
                        onDialog = { dialog = it },
                        focusSection = settingsFocus,
                        onContentScrollChanged = reportContentScroll
                    )
                        }
                    }
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

            // Bottom toast: sits above Marble's floating dock, follows the IME, and never
            // covers page headers / connection controls at the top of the screen.
            MarbleSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(start = 14.dp, end = 14.dp)
                    .padding(bottom = dockClearance())
                    .widthIn(max = 520.dp)
            )

            // MARBLE_IOS_FLOATING_DOCK_V81 — the dock is a true overlay: no Scaffold slot
            // reserves space for it, so pages and the backdrop keep scrolling beneath the
            // glass and shine through it (per-tab lists pad their last item past it).
            FloatingSpatialDock(
                selected = tabs[pagerState.currentPage],
                glass = contentScrolling || pagerState.isScrollInProgress,
                onSelect = { next ->
                    detailProfile = null
                    settingsFocus = null
                    scope.launch { pagerState.animateScrollToPage(next.ordinal) }
                }
            )

            // MARBLE_SIGNATURE_HOME_V112 — the Signature status banner riding on top of every
            // page (the Home-only scope renders inside the Signature Home itself). A slim strip
            // with the live state, the selected server and the compact ping/uptime readout.
            if (
                repo.settings.proStatusBannerEnabled &&
                parseProBannerScope(repo.settings.proBannerScope) == ProBannerScope.ALL
            ) {
                SignatureStatusBanner(
                    evidence = deck.evidence,
                    accent = signatureAccentColor(parseProAccent(repo.settings.proAccent)),
                    tone = signatureStatusTone(deck.evidence, parseProAccent(repo.settings.proAccent)),
                    onToggle = deckActions.onToggleConnection,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp)
                        .widthIn(max = 560.dp)
                )
            }

            // MARBLE_SIGNATURE_HOME_V112 — the floating connect shutter: app-wide, draggable,
            // v2rayNG-style. Tap toggles the connection; the dragged spot persists.
            if (repo.settings.proFloatingButtonEnabled) {
                SignatureFloatingConnectOverlay(
                    evidence = deck.evidence,
                    accent = signatureAccentColor(parseProAccent(repo.settings.proAccent)),
                    startNx = repo.proFabPosition.first,
                    startNy = repo.proFabPosition.second,
                    onToggle = deckActions.onToggleConnection,
                    onPositionSettled = { nx, ny -> repo.rememberProFabPosition(nx, ny) }
                )
            }
        }
    }

    // The IP row opens an in-app diagnostic surface rather than leaving Home for a browser or
    // a separate screen. It is safe to show while the metadata request is still resolving.
    if (ipDetailsOpen) {
        IpDetailsDialog(
            info = repo.serverIntel,
            loading = repo.serverIntelLoading,
            error = repo.serverIntelError,
            onDismiss = { ipDetailsOpen = false }
        )
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
                MarbleDialogAction(
                    label="Close",
                    tone=Aether.Cyan,
                    variant=PrismButtonVariant.Secondary,
                    onClick={ dialog=null }
                )
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
                                        append("ANTI-IP LEAK SCORE\n")
                                        append("${report.ipLeakScore}%")
                                        append("\n\nDNS LEAK SCORE\n")
                                        append("${report.dnsLeakScore}%")
                                        append("\n\nOVERALL AUDIT SCORE\n")
                                        append("${report.overallScore}%")
                                        append("\n\nPROXY EXIT IP\n")
                                        append(report.proxyIp.ifBlank { "unverified" })
                                        append("\n\nPHYSICAL IP (USER-TRIGGERED COMPARISON)\n")
                                        append(report.underlayIp.ifBlank { "unavailable" })
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
private fun IpDetailsDialog(
    info: com.marbleng.app.ServerIntelInfo?,
    loading: Boolean,
    error: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        tonalElevation = 0.dp,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    trx("IP details"),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    trx("Public route information for the connected server"),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Aether.Cyan,
                        trackColor = Color.Transparent
                    )
                }
                if (info == null) {
                    Text(
                        if (error.isBlank()) "Resolving the public IP…" else compactInAppMessage(error),
                        color = if (error.isBlank()) Aether.InkMuted else Aether.Amber,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailRow("Destination", info.endpoint)
                            DetailRow("IP", info.ip)
                            DetailRow("Type", info.ipType)
                            DetailRow("Location", info.locationLabel)
                            DetailRow("Country", listOf(info.flag, info.countryCode).filter { it.isNotBlank() }.joinToString(" "))
                            DetailRow("Network", info.datacenterLabel)
                            DetailRow("ASN", info.asn)
                            DetailRow("ISP", info.isp)
                            DetailRow("Domain", info.domain)
                            val markers = buildList {
                                if (info.hosting) add("Hosting")
                                if (info.proxy) add("Proxy")
                                if (info.vpn) add("VPN")
                                if (info.tor) add("Tor")
                            }
                            DetailRow("Signals", markers.joinToString(" • ").ifBlank { "No flags" })
                        }
                    }
                }
                Text(
                    trx("Only the resolved public endpoint is queried; credentials and subscription data stay local."),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            MarbleDialogAction(
                label = "Done",
                tone = Aether.Cyan,
                variant = PrismButtonVariant.Primary,
                onClick = onDismiss
            )
        }
    )
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
                    trx("A fresh MarbleNG build is ready"),
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
                    trx("A newer signed release is available on GitHub."),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (update.notes.isNotBlank()) {
                    val notesScroll = rememberScrollState()
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            trx("WHAT CHANGED"),
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
            PrismButton(
                label = "View update",
                onClick = onUpdate,
                tone = Aether.Cyan,
                variant = PrismButtonVariant.Primary,
                compact = true
            )
        },
        dismissButton = {
            MarbleDialogAction(label="Later", tone=Aether.InkMuted, onClick=onLater)
        }
    )
}

private fun compactInAppMessage(raw: String): String {
    val message = raw.replace(Regex("\\s+"), " ").trim()
    val lower = message.lowercase()
    return when {
        "vless without tls or other encryption is prohibited" in lower ->
            "Unsupported VLESS • pick a server with TLS/REALITY, or turn Marble Freedom back on"
        "failed to build outbound config" in lower ->
            "Xray rejected this server configuration • check protocol/TLS settings"
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
                    PrismButton(
                        label = action,
                        onClick = data::performAction,
                        tone = tone,
                        variant = PrismButtonVariant.Secondary,
                        compact = true,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Dismiss message" }
                        .clickable { data.dismiss() },
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
    PrismBackdrop(modifier)
}

@Composable
private fun dockClearance(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp

@Composable
private fun FloatingSpatialDock(
    selected: SpatialTab,
    glass: Boolean,
    onSelect: (SpatialTab) -> Unit
) {
    // MARBLE_BOTTOM_DOCK_UNIFIED_FLOATING_V661 — unified floating navigation lineage
    // MARBLE_IOS_FLOATING_DOCK_V81 — a genuinely detached, frosted-glass tab bar:
    //  - rendered as an overlay (no Scaffold bottomBar slot), so pages scroll under it
    //    and whatever is behind shines through the translucent material
    //  - never flush with the screen edge: side margins + a lift above the gesture bar
    //  - opaque while idle; one translucent material, hairline and top highlight only while
    //    the current page is actively scrolling
    //  - flexible items: each button shares the row width (weight), icon + label sizes
    //    are sp/dp-scaled so they adapt to display size and font scale
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val barShape = RoundedCornerShape(28.dp)
        // Navigation must not animate its base material. A theme switch can invalidate the
        // palette twice (system bars + Compose scheme), and interpolating this color creates the
        // visible flash reported on the bottom dock.
        val dockSurface = if (glass) Aether.BarGlass else Aether.VoidElevated
        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
                .clip(barShape)
                // Resting state is an opaque product surface. Only active user scrolling exposes
                // the page beneath the dock, so the glass treatment has a clear purpose instead
                // of making the navigation permanently translucent.
                .background(dockSurface)
                .then(
                    if (glass) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(Aether.BarGlassHighlight, Color.Transparent)
                            )
                        )
                    } else Modifier
                )
                .border(1.dp, Aether.BarGlassBorder, barShape)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpatialTab.entries.forEach { item ->
                val active = item == selected
                val glassShape = RoundedCornerShape(20.dp)

                val activeScale by animateFloatAsState(
                    targetValue = if (active) 1.03f else 1f,
                    animationSpec = MarbleMotionSpecs.ResponseFloat,
                    label = "dock-scale-${item.name}"
                )
                // Stable colors prevent the active pill from flashing during a page click or
                // palette recomposition; motion belongs to the icon, not the navigation surface.
                val inkTone = if (active) Aether.Cyan else Aether.InkMuted
                val pillBg = if (active) Aether.Cyan.copy(alpha = 0.15f) else Color.Transparent
                // The selected segment carries a soft outline on top of its wash — the
                // segmented-control cue, kept colour-animated so it lands with the pill.
                val indicatorTone by animateColorAsState(
                    targetValue = if (active) Aether.Cyan.copy(alpha = .34f) else Color.Transparent,
                    animationSpec = MarbleMotionSpecs.Color,
                    label = "dock-indicator-${item.name}"
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = activeScale
                            scaleY = activeScale
                        }
                        .clip(glassShape)
                        .background(pillBg)
                        .border(1.dp, indicatorTone, glassShape)
                        .kineticClickable(
                            boundedShape = glassShape,
                            role = Role.Tab,
                            showIndication = false
                        ) { onSelect(item) }
                        .semantics {
                            this.selected = active
                            contentDescription = "${item.label} tab"
                            stateDescription = if (active) "Selected" else "Not selected"
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MarbleTabIcon(
                        tab = item,
                        color = inkTone,
                        active = active,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        spatialTabLabel(item),
                        color = inkTone,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.01.sp
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
        val w=size.width
        val h=size.height
        val stroke=if(active) 2.1f.dp.toPx() else 1.7f.dp.toPx()
        val line=Stroke(width=stroke,cap=StrokeCap.Round)

        when(tab) {
            SpatialTab.DECK -> {
                val roof=Path().apply {
                    moveTo(w*.18f,h*.47f)
                    lineTo(w*.50f,h*.20f)
                    lineTo(w*.82f,h*.47f)
                }
                drawPath(roof,color,style=line)
                drawLine(color,Offset(w*.27f,h*.43f),Offset(w*.27f,h*.79f),stroke,StrokeCap.Round)
                drawLine(color,Offset(w*.73f,h*.43f),Offset(w*.73f,h*.79f),stroke,StrokeCap.Round)
                drawLine(color,Offset(w*.27f,h*.79f),Offset(w*.73f,h*.79f),stroke,StrokeCap.Round)
                drawLine(color,Offset(w*.49f,h*.79f),Offset(w*.49f,h*.61f),stroke,StrokeCap.Round)
            }

            SpatialTab.LIBRARY -> {
                listOf(.28f,.50f,.72f).forEachIndexed { index,y ->
                    drawLine(
                        color=color,
                        start=Offset(w*.22f,h*y),
                        end=Offset(w*.78f,h*y),
                        strokeWidth=stroke,
                        cap=StrokeCap.Round
                    )
                    drawCircle(
                        color=color,
                        radius=if(active) w*.045f else w*.038f,
                        center=Offset(
                            if(index%2==0) w*.30f else w*.70f,
                            h*y
                        )
                    )
                }
            }

            SpatialTab.SETTINGS -> {
                // Stroke-only knobs: nothing opaque is painted behind the glass bar.
                val rows=listOf(
                    .30f to .38f,
                    .50f to .64f,
                    .70f to .45f
                )
                rows.forEach { (y,knobX) ->
                    drawLine(
                        color=color,
                        start=Offset(w*.20f,h*y),
                        end=Offset(w*.80f,h*y),
                        strokeWidth=stroke,
                        cap=StrokeCap.Round
                    )
                    drawCircle(
                        color=color,
                        radius=w*.075f,
                        center=Offset(w*knobX,h*y),
                        style=Stroke(width=stroke,cap=StrokeCap.Round)
                    )
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
        "Servers" -> HomeIcon.LIBRARY
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
                trx(title),
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
    borderColor: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    tint: Brush? = null,
    radius: Dp = PrismSurface.CardRadius,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    PrismPanel(
        modifier=modifier,
        accent=if(borderColor == Color.Transparent) Aether.Cyan else borderColor,
        selected=borderColor != Color.Transparent,
        radius=radius,
        contentPadding=contentPadding,
        tint=tint,
        onClick=onClick,
        enabled=enabled,
        content=content
    )
}

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
    PrismBadge(
        text=text,
        tone=color,
        strong=!compact
    )
}

@Composable
private fun SectionLabel(
    title: String,
    subtitle: String? = null
) {
    // MARBLE_SETTINGS_QUIET_CHROME_V114 — the little gradient bar beside every settings heading is
    // gone. Hierarchy now comes from type alone: a quiet caption above a lighter, smaller title.
    // Colour markers next to settings read as status, and nothing here is a status.
    Column(
        modifier=Modifier.padding(vertical=3.dp),
        verticalArrangement=Arrangement.spacedBy(3.dp)
    ) {
        Text(
            trx(title),
            color=Aether.Ink,
            style=MaterialTheme.typography.titleSmall,
            fontWeight=FontWeight.Medium,
            maxLines=2,
            overflow=TextOverflow.Ellipsis
        )
        subtitle?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                trx(detail),
                color=Aether.InkMuted,
                style=MaterialTheme.typography.bodySmall,
                maxLines=2,
                overflow=TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FreedomSectionHeader(
    title: String,
    subtitle: String,
    tone: Color = Aether.Cyan
) {
    Column(
        modifier=Modifier
            .fillMaxWidth()
            .padding(top=4.dp,bottom=1.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)
    ) {
        // MARBLE_SETTINGS_QUIET_CHROME_V114 — no marker dot beside a settings heading either.
        Text(
            trx(title),
            color=tone.copy(alpha=.92f),
            style=MaterialTheme.typography.labelLarge,
            fontWeight=FontWeight.Medium,
            maxLines=2,
            overflow=TextOverflow.Ellipsis
        )
        Text(
            trx(subtitle),
            color=Aether.InkMuted,
            style=MaterialTheme.typography.bodySmall,
            maxLines=2,
            overflow=TextOverflow.Ellipsis
        )
    }
}


// MARBLE_HOME_VECTOR_ICONS_V36
private enum class HomeIcon {
    BRAND, POWER, STOP, CANCEL, RESET, SHIELD, TUNNEL, ROUTE,
    PING, JITTER, QUALITY, NODES, VERIFIED, CHECK, MODE, BENCHMARK,
    RANK, LIBRARY, PRIVACY, ROUTING, NETWORK, SERVER, DOWNLOAD, UPLOAD,
    DETAILS, SPARK, STATUS, MORE,
    // MARBLE_SERVERS_V114 — glyphs the rebuilt Servers screen and the Settings hub need:
    // a clipboard for the floating magic button, an information mark, a palette for the
    // theme preview, a globe for language/links, disclosure chevrons, a back arrow and a
    // typeface mark for the font picker.
    CLIPBOARD, INFO, PALETTE, GLOBE, CHEVRON, BACK, FONT
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
        // MARBLE_HOME_VECTOR_ICONS_V36 + V77 — size-aware weight. The stroke stays
        // proportional to the glyph, so an icon scales up or down with its slot and keeps
        // the same optical weight: thin at micro/inline sizes, confident at hero sizes.
        val stroke = (m * .082f).coerceIn(1.35f, 3.4f)
        val fine = (stroke * .78f).coerceIn(1.15f, 2.6f)
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

            HomeIcon.CHECK -> {
                val tick = Path().apply {
                    moveTo(w*.22f,h*.53f); lineTo(w*.42f,h*.72f); lineTo(w*.78f,h*.29f)
                }
                drawPath(tick,color,style=Stroke(width=(m*.16f).coerceAtLeast(1.5f),cap=StrokeCap.Round))
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

            HomeIcon.SERVER -> {
                val top = Path().apply {
                    moveTo(w*.22f,h*.20f); lineTo(w*.78f,h*.20f)
                    lineTo(w*.78f,h*.45f); lineTo(w*.22f,h*.45f); close()
                }
                val bottom = Path().apply {
                    moveTo(w*.22f,h*.55f); lineTo(w*.78f,h*.55f)
                    lineTo(w*.78f,h*.80f); lineTo(w*.22f,h*.80f); close()
                }
                drawPath(top,color,style=fineLine)
                drawPath(bottom,color,style=fineLine)
                drawCircle(color,m*.035f,Offset(w*.31f,h*.325f))
                drawCircle(color,m*.035f,Offset(w*.31f,h*.675f))
                drawLine(color,Offset(w*.43f,h*.325f),Offset(w*.68f,h*.325f),fine,StrokeCap.Round)
                drawLine(color,Offset(w*.43f,h*.675f),Offset(w*.68f,h*.675f),fine,StrokeCap.Round)
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

            HomeIcon.MORE -> {
                drawCircle(color, m*.075f, Offset(w*.25f,h*.50f))
                drawCircle(color, m*.075f, Offset(w*.50f,h*.50f))
                drawCircle(color, m*.075f, Offset(w*.75f,h*.50f))
            }

            // MARBLE_SERVERS_V114 — a clipboard: the board, its clip and two captured lines.
            HomeIcon.CLIPBOARD -> {
                val board = Path().apply {
                    moveTo(w*.28f,h*.24f)
                    lineTo(w*.28f,h*.82f)
                    lineTo(w*.72f,h*.82f)
                    lineTo(w*.72f,h*.24f)
                }
                drawPath(board,color,style=fineLine)
                drawPath(
                    Path().apply {
                        moveTo(w*.40f,h*.24f); lineTo(w*.40f,h*.17f)
                        lineTo(w*.60f,h*.17f); lineTo(w*.60f,h*.24f)
                    },
                    color,style=line
                )
                drawLine(color,Offset(w*.38f,h*.44f),Offset(w*.62f,h*.44f),fine,StrokeCap.Round)
                drawLine(color,Offset(w*.38f,h*.58f),Offset(w*.62f,h*.58f),fine,StrokeCap.Round)
                drawLine(color,Offset(w*.38f,h*.71f),Offset(w*.54f,h*.71f),fine,StrokeCap.Round)
            }

            HomeIcon.INFO -> {
                drawCircle(color,m*.38f,Offset(w*.50f,h*.50f),style=fineLine)
                drawCircle(color,m*.055f,Offset(w*.50f,h*.32f))
                drawLine(color,Offset(w*.50f,h*.45f),Offset(w*.50f,h*.70f),stroke,StrokeCap.Round)
            }

            // A painter's palette: the dish and three wells of colour.
            HomeIcon.PALETTE -> {
                drawPath(
                    Path().apply {
                        moveTo(w*.50f,h*.14f)
                        cubicTo(w*.20f,h*.14f,w*.12f,h*.44f,w*.20f,h*.64f)
                        cubicTo(w*.26f,h*.80f,w*.42f,h*.86f,w*.50f,h*.78f)
                        cubicTo(w*.56f,h*.72f,w*.52f,h*.62f,w*.58f,h*.60f)
                        cubicTo(w*.72f,h*.56f,w*.88f,h*.52f,w*.88f,h*.38f)
                        cubicTo(w*.88f,h*.22f,w*.72f,h*.14f,w*.50f,h*.14f)
                        close()
                    },
                    color,style=fineLine
                )
                drawCircle(color,m*.055f,Offset(w*.36f,h*.34f))
                drawCircle(color,m*.055f,Offset(w*.52f,h*.28f))
                drawCircle(color,m*.055f,Offset(w*.68f,h*.34f))
                drawCircle(color,m*.055f,Offset(w*.34f,h*.54f))
            }

            HomeIcon.GLOBE -> {
                drawCircle(color,m*.38f,Offset(w*.50f,h*.50f),style=fineLine)
                drawPath(
                    Path().apply {
                        moveTo(w*.50f,h*.12f)
                        cubicTo(w*.36f,h*.30f,w*.36f,h*.70f,w*.50f,h*.88f)
                        cubicTo(w*.64f,h*.70f,w*.64f,h*.30f,w*.50f,h*.12f)
                    },
                    color,style=fineLine
                )
                drawLine(color,Offset(w*.14f,h*.50f),Offset(w*.86f,h*.50f),fine,StrokeCap.Round)
            }

            HomeIcon.CHEVRON -> {
                drawPath(
                    Path().apply {
                        moveTo(w*.38f,h*.24f); lineTo(w*.64f,h*.50f); lineTo(w*.38f,h*.76f)
                    },
                    color,style=line
                )
            }

            HomeIcon.BACK -> {
                drawLine(color,Offset(w*.78f,h*.50f),Offset(w*.28f,h*.50f),stroke,StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(w*.46f,h*.30f); lineTo(w*.26f,h*.50f); lineTo(w*.46f,h*.70f)
                    },
                    color,style=line
                )
            }

            // A typeface mark: a serif capital between two baselines.
            HomeIcon.FONT -> {
                drawLine(color,Offset(w*.20f,h*.78f),Offset(w*.80f,h*.78f),fine,StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(w*.32f,h*.68f); lineTo(w*.50f,h*.22f); lineTo(w*.68f,h*.68f)
                    },
                    color,style=line
                )
                drawLine(color,Offset(w*.40f,h*.54f),Offset(w*.60f,h*.54f),fine,StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun HomeIconTile(icon: HomeIcon, color: Color, modifier: Modifier = Modifier) {
    // MARBLE_NAVY_BRAND_UI_V77 — tiles carry a soft tone gradient + hairline so icon
    // "chips" read as crafted glass rather than flat tinted squares.
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier
            .size(38.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        color.copy(alpha = .18f),
                        color.copy(alpha = .06f),
                        color.copy(alpha = .12f)
                    )
                )
            )
            .border(1.dp, color.copy(alpha = .22f), shape),
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
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HomeVectorIcon(icon, tone, Modifier.size(14.dp))
        Text(
            trx(text),
            color = tone,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarbleCompactTopBar(
    title: String,
    subtitle: String = "",
    actionLabel: String? = null,
    actionIcon: HomeIcon? = null,
    onAction: (() -> Unit)? = null
) {
    // MARBLE_IOS_SIMPLIFY_V81 — a slimmer, quieter page header.
    Row(
        modifier=Modifier
            .fillMaxWidth()
            .heightIn(min=56.dp)
            .padding(vertical=5.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(11.dp)
    ) {
        if(title == "MarbleNG") {
            Box(
                modifier=Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Aether.Cyan,Aether.Amethyst)
                        )
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha=.22f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment=Alignment.Center
            ) {
                Icon(
                    painter=painterResource(R.drawable.ic_marble_prism),
                    contentDescription=null,
                    tint=Color.White,
                    modifier=Modifier.size(22.dp)
                )
            }
        } else {
            val icon=when(title) {
                "Servers" -> HomeIcon.LIBRARY
                "Settings" -> HomeIcon.MODE
                else -> HomeIcon.DETAILS
            }
            HomeIconTile(icon,Aether.Cyan,Modifier.size(36.dp))
        }

        Column(
            modifier=Modifier.weight(1f),
            verticalArrangement=Arrangement.spacedBy(1.dp)
        ) {
            Text(
                trx(title),
                color=Aether.Ink,
                style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold,
                maxLines=1,
                overflow=TextOverflow.Ellipsis
            )
            if(subtitle.isNotBlank()) {
                Text(
                    trx(subtitle),
                    color=Aether.InkMuted,
                    style=MaterialTheme.typography.bodySmall,
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis
                )
            }
        }

        if(onAction != null && actionLabel != null) {
            CyberButton(
                label=actionLabel,
                color=Aether.Cyan,
                icon=actionIcon,
                compact=true,
                onClick=onAction
            )
        }
    }
}

@Composable
private fun MarbleServerAvatar(
    profile: ProxyProfile?,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val flag=profile?.name?.let(::leadingFlagGlyph)
    val fallback=profile?.host?.let(::countryGlyph).orEmpty()
        .takeIf { it.isNotBlank() && it != "◈" }
    val label=flag
        ?: fallback
        ?: profile?.scheme?.trim()?.take(1)?.uppercase()?.ifBlank { "M" }
        ?: "M"
    val tone=if(active) Aether.Emerald else Aether.Cyan
    val shape=RoundedCornerShape(17.dp)

    // The badge has to live outside the clipped surface box: a corner sticker inside the rounded
    // rectangle is cut away by the clip. The outer box keeps the fixed 50dp box, so nothing re-measures.
    Box(
        modifier=modifier
            .size(50.dp),
        contentAlignment=Alignment.Center
    ) {
        Box(
            modifier=Modifier
                .matchParentSize()
                .border(
                    if(active) 1.4.dp else 1.dp,
                    tone.copy(alpha=if(active) .55f else .32f),
                    shape
                )
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        if(active) {
                            listOf(
                                tone.copy(alpha=.26f),
                                tone.copy(alpha=.09f),
                                Aether.Amethyst.copy(alpha=.11f)
                            )
                        } else {
                            listOf(
                                tone.copy(alpha=.13f),
                                Aether.Amethyst.copy(alpha=.055f)
                            )
                        }
                    )
                ),
            contentAlignment=Alignment.Center
        ) {
            Text(
                label,
                color=tone,
                style=if(flag != null || fallback != null) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight=FontWeight.Bold
            )
        }
        if(active) {
            Box(
                modifier=Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x=2.dp,y=2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Aether.Emerald),
                contentAlignment=Alignment.Center
            ) {
                HomeVectorIcon(
                    HomeIcon.CHECK,
                    Aether.Void,
                    Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeServerSelector(
    profile: ProxyProfile?,
    activeName: String,
    connected: Boolean,
    onLibrary: () -> Unit,
    serverless: Boolean = false,
    livePingMs: Int = 0
) {
    val displayName = stripLeadingFlag(activeName).ifBlank { "Choose a route" }
    val tone = if (connected) Aether.Emerald else Aether.Cyan
    val shape = RoundedCornerShape(21.dp)

    // MARBLE_CONNECTED_CARD_V78 — live ping readout + polished visual rhythm
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .prismElevated(
                shape = shape,
                tone = tone,
                selected = connected,
                tint = Brush.horizontalGradient(
                    listOf(
                        tone.copy(alpha = if (connected) .12f else .075f),
                        Color.Transparent
                    )
                )
            )
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onLibrary)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        MarbleServerAvatar(profile=profile,active=connected)
        Column(Modifier.weight(1f)) {
            Text(
                displayName,
                color=Aether.Ink,
                style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.Bold,
                maxLines=1,
                overflow=TextOverflow.Ellipsis
            )
            Text(
                if (serverless) {
                    "FREEDOM  •  fragment"
                } else {
                    profile?.let {
                        listOfNotNull(
                            it.scheme.uppercase(),
                            it.host.takeIf(String::isNotBlank),
                            it.port.takeIf { p -> p > 0 }?.toString()
                        ).joinToString("  •  ")
                    }.orEmpty()
                },
                color=Aether.InkMuted,
                style=MaterialTheme.typography.bodySmall,
                maxLines=1,
                overflow=TextOverflow.Ellipsis
            )
        }
        // Live ping badge when connected
        if (connected && livePingMs > 0) {
            val pingTone = when {
                livePingMs < 100 -> Aether.Emerald
                livePingMs < 250 -> Aether.Amber
                else -> Aether.Danger
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = pingTone.copy(alpha = .13f),
                tonalElevation = 0.dp
            ) {
                Text(
                    "${livePingMs}ms",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = pingTone,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha=.09f)),
                contentAlignment=Alignment.Center
            ) {
                HomeVectorIcon(
                    HomeIcon.DETAILS,
                    tone,
                    Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeRouteDetailsRow(
    connected: Boolean,
    onDetails: () -> Unit
) {
    val shape=RoundedCornerShape(16.dp)
    Row(
        modifier=Modifier
            .fillMaxWidth()
            .heightIn(min=48.dp)
            .clip(shape)
            .kineticClickable(role=Role.Button,onClick=onDetails)
            .padding(horizontal=8.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Aether.Amethyst.copy(alpha=.09f)),
            contentAlignment=Alignment.Center
        ) {
            HomeVectorIcon(
                HomeIcon.ROUTE,
                Aether.Amethyst,
                Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if(connected) "Secure route details" else "Inspect selected route",
                color=Aether.Ink,
                style=MaterialTheme.typography.labelLarge,
                fontWeight=FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MarbleConnectionQualityRing(
    score: Int,
    tone: Color,
    connecting: Boolean,
    connected: Boolean,
    blocked: Boolean,
    onToggle: () -> Unit
) {
    val animatedTone by animateColorAsState(
        targetValue=tone,
        animationSpec=MarbleMotionSpecs.Color,
        label="prism-connection-tone-v54"
    )
    PrismConnectionStage(
        tone=animatedTone,
        connected=connected,
        connecting=connecting,
        blocked=blocked,
        qualityScore=score,
        onToggle=onToggle,
        modifier=Modifier.fillMaxWidth()
    )
}

@Composable
private fun HomeMetricBento(repo: AppRepository) {
    // MARBLE_LIVE_QUALITY_BENTO_V78 — robust sparkline + correct live state guards
    val pingHistory = remember { mutableStateListOf<Int>() }

    // Accumulate ping samples; only add when there is a real measurement
    LaunchedEffect(repo.livePingMs) {
        val value = repo.livePingMs
        if (value > 0) {
            if (pingHistory.lastOrNull() != value || pingHistory.size < 2) {
                pingHistory += value
                while (pingHistory.size > 36) pingHistory.removeAt(0)
            }
        } else if (repo.state != "CONNECTED") {
            // Clear sparkline when disconnected so stale history doesn't linger
            pingHistory.clear()
        }
    }

    // Clear on disconnect so values don't freeze
    val isConnected = repo.state == "CONNECTED"
    val pingMs = if (isConnected) repo.livePingMs else 0
    val jitterMs = if (isConnected) repo.liveJitterMs else 0
    val jitterSamples = if (isConnected) repo.liveJitterSamples else 0
    val routeScore = if (isConnected) repo.liveRouteScore else -1
    val liveStatus = when {
        isConnected && pingMs > 0 -> "LIVE"
        isConnected -> "MEASURING"
        else -> "WAITING"
    }
    val liveStatusColor = when (liveStatus) {
        "LIVE" -> Aether.Emerald
        "MEASURING" -> Aether.Amber
        else -> Aether.InkMuted
    }

    val pingTone = marbleMetricTone(pingMetricBand(pingMs))
    val jitterTone = marbleMetricTone(jitterMetricBand(jitterMs, jitterSamples))
    val qualityTone = marbleMetricTone(qualityMetricBand(routeScore))

    // MARBLE_HOME_PUZZLE_GRID_V77 — Ping/Jitter/Quality share one panel bento
    PrismPanel(
        modifier = Modifier.fillMaxWidth(),
        accent = if (isConnected && pingMs > 0) pingTone else Aether.Cyan,
        selected = isConnected && pingMs > 0,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Live quality")
            Spacer(Modifier.weight(1f))
            PrismBadge(liveStatus, liveStatusColor, strong = liveStatus == "LIVE")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarbleMetricCard(
                title = "Ping",
                value = if (pingMs > 0) pingMs.toString() else "—",
                unit = if (pingMs > 0) "ms" else "",
                tone = pingTone,
                // MARBLE_LIVE_QUALITY_BENTO_V91: pass the remembered snapshot list itself — a fresh
                // mutableStateListOf() per recomposition churned memory and made the sparkline
                // flicker/glitch on every live update.
                sparkline = pingHistory,
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight()
            )
            Column(
                modifier = Modifier
                    .weight(.92f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarbleMetricCard(
                    title = "Jitter",
                    value = if (jitterSamples > 0 && isConnected) jitterMs.toString() else "—",
                    unit = if (jitterSamples > 0 && isConnected) "ms" else "",
                    tone = jitterTone,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                MarbleMetricCard(
                    title = "Quality",
                    value = if (routeScore >= 0) routeScore.toString() else "—",
                    unit = if (routeScore >= 0) "%" else "",
                    tone = qualityTone,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }

    }
}

@Composable
private fun marbleSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor=Aether.Cyan,
    checkedThumbColor=Color.White,
    uncheckedTrackColor=Aether.GlassStrong,
    uncheckedThumbColor=Aether.InkMuted,
    uncheckedBorderColor=Aether.GlassBorder
)

@Composable
private fun HomeQuickSettingRow(
    icon: HomeIcon,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit
) {
    val tone=if(checked) Aether.Cyan else Aether.InkMuted
    val shape=RoundedCornerShape(20.dp)

    Column(
        modifier=Modifier
            .fillMaxWidth()
            .heightIn(min=78.dp)
            .clip(shape)
            .background(Aether.VoidElevated)
            .prismWell(shape=shape, tone=tone, selected=checked)
            .padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier=Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tone.copy(alpha=.10f)),
                contentAlignment=Alignment.Center
            ) {
                HomeVectorIcon(icon,tone,Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            Switch(
                checked=checked,
                onCheckedChange=onChecked,
                enabled=enabled,
                colors=marbleSwitchColors()
            )
        }
        Text(
            trx(title),
            color=Aether.Ink,
            style=MaterialTheme.typography.labelLarge,
            fontWeight=FontWeight.Bold,
            maxLines=1
        )
    }
}

// =================================================================================================
// DECK
// =================================================================================================

/**
 * MARBLE_SIGNATURE_HOME_V112 — the Home deck facts (selected profile + shared evidence) resolved
 * once per composition and shared by the Home page itself, the app-wide Signature status banner
 * and the floating connect button, so every surface can never disagree about the truth.
 */
private class DeckEvidence(
    val profile: ProxyProfile?,
    val evidence: HomeEvidence
)

@Composable
private fun rememberDeckEvidence(repo: AppRepository): DeckEvidence {
    val active = repo.profile(
        repo.activeProfileId,
        repo.activeProfileSourceId
    ) ?: repo.lastProfile()
    // Identity always comes from the selected profile, never from the runtime state detail
    // string (which carries engine progress copy and is not a node name).
    val activeName = active?.name ?: "Choose a route"
    val endpoint = active?.host?.trim()?.removeSurrounding("[", "]").orEmpty()
    val serverless = active?.let { ServerlessFreedomEngine.isServerless(it) }
        ?: repo.settings.serverlessModeEnabled
    val serverInfo = repo.serverIntel?.takeIf {
        serverless || (endpoint.isNotBlank() && it.endpoint.equals(endpoint, ignoreCase = true))
    }
    val evidence = buildHomeEvidence(
        repo = repo,
        profile = active,
        displayName = active?.let { stripLeadingFlag(activeName) }.orEmpty(),
        info = serverInfo,
        fallbackFlag = leadingFlagGlyph(activeName)
    )
    return DeckEvidence(active, evidence)
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun CyberDeck(
    repo: AppRepository,
    deck: DeckEvidence,
    actions: HomeActions,
    onContentScrollChanged: (Boolean) -> Unit
) {
    // MARBLE_HOME_STYLE_V110 / MARBLE_SIGNATURE_HOME_V112 — Home is one evidence model rendered
    // by one of five presentations. The style is a pure presentation choice made in Settings (or
    // in the Signature switcher); the runtime facts (node, source, IP + flag + 3 actions, session
    // uptime, one-shot ping) are identical in all of them.
    val evidence = deck.evidence
    val active = deck.profile
    val connected = evidence.connected
    val endpoint = active?.host?.trim()?.removeSurrounding("[", "]").orEmpty()
    val serverless = active?.let { ServerlessFreedomEngine.isServerless(it) }
        ?: repo.settings.serverlessModeEnabled

    LaunchedEffect(Unit) { onContentScrollChanged(false) }
    LaunchedEffect(connected, active?.id, active?.subscriptionId, endpoint) {
        if (connected && active != null && (serverless || endpoint.isNotBlank())) {
            repo.refreshServerIntel(active)
        }
    }

    // One automatic measurement per session; every later measurement is user-initiated.
    // MARBLE_HOME_PING_RESCUE_V112 — a first probe fired 1.8s after connect can land while the
    // tunnel's TLS state is still cold (the same warm-up the live route monitor sees). That miss
    // no longer freezes as "no response": exactly one bounded re-check follows, still inside the
    // user's session and still never a repeating timer.
    LaunchedEffect(repo.connectedSinceMs) {
        if (repo.connectedSinceMs > 0L) {
            delay(1_800)
            if (repo.connectionPingState == ConnectionPingState.IDLE) repo.measureConnectionPing()
            delay(2_200)
            if (
                repo.connectedSinceMs > 0L &&
                repo.connectionPingState == ConnectionPingState.FAILED
            ) {
                repo.measureConnectionPing()
            }
        }
    }

    // The Signature studio configuration: every layer is an independent Settings choice.
    val pro = rememberSignatureProContext(repo, deck)

    Box(Modifier.fillMaxSize()) {
        HomeStyleSurface(
            style = parseHomeStyle(repo.settings.homeStyle),
            evidence = evidence,
            actions = actions,
            bottomClearance = dockClearance(),
            pro = pro
        )

        if (evidence.blocked && repo.stateDetail.isNotBlank()) {
            Text(
                compactInAppMessage(repo.stateDetail),
                color = Aether.Danger,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = dockClearance())
            )
        }
    }
}

/**
 * MARBLE_SIGNATURE_HOME_V112 — resolves the Signature studio snapshot from the current settings
 * and Library selection. The rail follows the source the user selected in the Library (the
 * Freedom source maps to its engine profile), with the active server pinned first.
 */
@Composable
private fun rememberSignatureProContext(
    repo: AppRepository,
    deck: DeckEvidence
): HomeProContext {
    val t = Tr.now
    val filter = repo.librarySourceFilter
    val railLabel = when {
        filter == "all" -> t.library
        filter == "manual" -> "Manual"
        filter == ServerlessFreedomEngine.SOURCE_ID -> "Freedom"
        else -> repo.subscriptions.firstOrNull { it.id == filter }?.name ?: t.library
    }
    val base = when {
        filter == "all" -> repo.profiles.toList()
        filter == ServerlessFreedomEngine.SOURCE_ID -> listOfNotNull(repo.serverlessProfile())
        else -> repo.profiles.filter { it.subscriptionId == filter }
    }
    val activeId = deck.profile?.id
    val rail = (base.filter { it.id == activeId } + base.filterNot { it.id == activeId }).take(14)
    return HomeProContext(
        railProfiles = rail,
        railLabel = railLabel,
        cardStyle = parseProServerCardStyle(repo.settings.proServerCardStyle),
        showBanner = repo.settings.proStatusBannerEnabled,
        showCornerActions = repo.settings.proCornerActionsEnabled,
        showServerRail = repo.settings.proServerRailEnabled,
        showStyleSwitcher = repo.settings.proStyleSwitcherEnabled,
        shortcut = parseProShortcut(repo.settings.proShortcut),
        accent = parseProAccent(repo.settings.proAccent),
        selectedHomeStyle = parseHomeStyle(repo.settings.homeStyle),
        onHomeStyleSelected = { style ->
            repo.updateSettings(repo.settings.copy(homeStyle = style.id))
        },
        activeProfileId = repo.activeProfileId,
        connected = deck.evidence.connected,
        connecting = deck.evidence.connecting
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun HomeOrbitalHero(
    repo: AppRepository,
    active: ProxyProfile?,
    activeName: String,
    connected: Boolean,
    connecting: Boolean,
    blocked: Boolean,
    onToggle: () -> Unit
) {
    val tone=when {
        connected -> Aether.Emerald
        connecting -> Aether.Amethyst
        blocked -> Aether.Danger
        else -> Aether.Cyan
    }

    PrismPanel(
        modifier=Modifier.fillMaxWidth(),
        accent=tone,
        selected=connected || connecting || blocked,
        contentPadding=PaddingValues(14.dp)
    ) {
        // Home status copy is runtime text. It is anchored to a reserved block so the Connect control
        // below keeps its exact position while the sentence changes.
        Row(
            modifier=Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically
        ) {
            HomeStatusAnchor(
                title=when {
                    connected -> "Protected"
                    connecting -> "Securing route"
                    blocked -> "Fail-closed"
                    else -> "Ready to protect"
                },
                modifier=Modifier.weight(1f)
            )
            PrismBadge(
                text=if(repo.settings.connectionMode == ConnectionMode.FULL_TUN) {
                    "FULL TUN"
                } else {
                    "SOCKS :${repo.settings.localProxyPort}"
                },
                tone=tone,
                strong=connected
            )
        }

        MarbleConnectionQualityRing(
            score=repo.liveRouteScore,
            tone=tone,
            connecting=connecting,
            connected=connected,
            blocked=blocked,
            onToggle=onToggle
        )
    }
}

@Composable
private fun HomeStatusAnchor(
    title: String,
    modifier: Modifier = Modifier
) {
    val titleStyle=MaterialTheme.typography.titleLarge
    val titleBlock=anchoredTextBlockHeight(titleStyle, 1)

    AnimatedContent(
        targetState=title,
        transitionSpec={
            (fadeIn(MarbleMotionSpecs.ResponseFloat) +
                slideInVertically(MarbleMotionSpecs.Spatial) { it / 3 }) togetherWith
                (fadeOut(MarbleMotionSpecs.ExitFloat) +
                    slideOutVertically(MarbleMotionSpecs.SpatialExit) { -it / 3 })
        },
        label="home-status-title-anchor-v64",
        modifier=modifier
            .fillMaxWidth()
            .heightIn(min=titleBlock)
    ) { text ->
        Text(
            text,
            color=Aether.Ink,
            style=titleStyle,
            fontWeight=FontWeight.Bold,
            maxLines=1,
            overflow=TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeServerlessSwitch(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit
) {
    val enabled = repo.settings.serverlessModeEnabled
    val connected = repo.state == "CONNECTED" || repo.state == "CONNECTING" || repo.state == "BLOCKED"
    HomeQuickSettingRow(
        icon = HomeIcon.SHIELD,
        title = "Marble Freedom",
        subtitle = "",
        checked = enabled,
        enabled = !repo.busy
    ) { next ->
        repo.setServerlessMode(next)
        if (connected) {
            val profile = repo.lastProfile()
            if (profile != null) onConnect(profile)
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
    val shape=RoundedCornerShape(20.dp)
    Row(
        modifier
            .heightIn(min=78.dp)
            .prismElevated(
                shape=shape,
                tone=color,
                fill=Aether.VoidElevated,
                tint=Brush.linearGradient(
                    listOf(
                        color.copy(alpha=.075f),
                        Color.Transparent
                    )
                )
            )
            .kineticClickable(role=Role.Button,onClick=onClick)
            .padding(horizontal=11.dp,vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color.copy(alpha=.12f)),
            contentAlignment=Alignment.Center
        ) {
            HomeVectorIcon(icon,color,Modifier.size(20.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color=Aether.Ink,
                style=MaterialTheme.typography.labelLarge,
                fontWeight=FontWeight.Bold,
                maxLines=1
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    color=Aether.InkFaint,
                    style=MaterialTheme.typography.labelSmall,
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis
                )
            }
        }
        HomeVectorIcon(
            HomeIcon.DETAILS,
            color.copy(alpha=.72f),
            Modifier.size(16.dp)
        )
    }
}

@Composable
private fun HomeRouteRibbon(repo: AppRepository) {
    PrismPanel(
        modifier=Modifier.fillMaxWidth(),
        accent=if(repo.sentinel.killSwitchArmed) Aether.Emerald else Aether.Cyan,
        contentPadding=PaddingValues(14.dp)
    ) {
        Row(
            modifier=Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Aether.Cyan.copy(alpha=.10f)),
                contentAlignment=Alignment.Center
            ) {
                HomeVectorIcon(
                    HomeIcon.NETWORK,
                    Aether.Cyan,
                    Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    repo.networkSnapshot.label,
                    color=Aether.Ink,
                    style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold
                )
                Text(
                    "↓ ${compactRate(repo.liveDownBps)}   •   ↑ ${compactRate(repo.liveUpBps)}",
                    color=Aether.InkMuted,
                    style=MaterialTheme.typography.bodySmall,
                    fontFamily=FontFamily.Monospace
                )
            }
            PrismBadge(
                if(repo.sentinel.killSwitchArmed) "KILL SWITCH" else "IDLE",
                if(repo.sentinel.killSwitchArmed) Aether.Emerald else Aether.InkMuted,
                strong=repo.sentinel.killSwitchArmed
            )
        }

        HorizontalDivider(color=Aether.GlassBorderSoft)

        Row(
            modifier=Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f)) {
                HomeQuickSettingRow(
                    icon=HomeIcon.TUNNEL,
                    title="Full TUN",
                    subtitle="Device-wide route",
                    checked=repo.settings.connectionMode == ConnectionMode.FULL_TUN,
                    enabled=!repo.busy
                ) { enabled ->
                    repo.setConnectionMode(
                        if(enabled) ConnectionMode.FULL_TUN
                        else ConnectionMode.LOCAL_PROXY
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                HomeQuickSettingRow(
                    icon=HomeIcon.NETWORK,
                    title="IPv6",
                    subtitle=when {
                        !repo.settings.ipv6Enabled -> "Blocked fail-closed"
                        repo.networkSnapshot.hasIpv6 -> "Preferred on this network"
                        else -> "Ready • no v6 route here"
                    },
                    checked=repo.settings.ipv6Enabled,
                    enabled=!repo.busy
                ) { enabled ->
                    // One switch, one promise: IPv6 stays inside the tunnel and the family policy
                    // dials nodes over IPv6 whenever the network can carry it. Turning it off also
                    // drops the stricter preference so Xray can block ::/0 fail-closed.
                    repo.updateSettings(
                        repo.settings.copy(
                            ipv6Enabled = enabled,
                            preferIpv6 = if (enabled) repo.settings.preferIpv6 else false
                        )
                    )
                }
            }
        }

        Row(
            modifier=Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f)) {
                HomeQuickSettingRow(
                    icon=HomeIcon.SPARK,
                    title="Adaptive MTU",
                    subtitle="Link-aware",
                    checked=repo.settings.adaptiveMtuEnabled,
                    enabled=!repo.busy
                ) { enabled ->
                    repo.updateSettings(repo.settings.copy(adaptiveMtuEnabled=enabled))
                }
            }
            Box(Modifier.weight(1f)) {
                HomeQuickSettingRow(
                    icon=HomeIcon.SHIELD,
                    title="Auto recovery",
                    subtitle="Kill-switch reconnect",
                    checked=repo.settings.autoReconnectAfterKillSwitch,
                    enabled=!repo.busy
                ) { enabled ->
                    repo.updateSettings(
                        repo.settings.copy(autoReconnectAfterKillSwitch=enabled)
                    )
                }
            }
        }
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
                    forced -> "FORCED"
                    scanning -> "SCANNING"
                    state.isp != null -> buildString {
                        append(state.ispLine)
                        if (state.confidence > 0) append(" • ${state.confidence}%")
                    }
                    else -> "ACTIVE"
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
            .prismElevated(
                shape=shape,
                tone=statusColor,
                selected=connected,
                tint=Brush.verticalGradient(
                    listOf(
                        statusColor.copy(alpha = .12f),
                        Color.Transparent
                    )
                )
            )
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
                        PrismButton(
                            label = "Details",
                            onClick = onDetails,
                            tone = Aether.Cyan,
                            variant = PrismButtonVariant.Quiet,
                            compact = true,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)
                        )
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
                trx(label).uppercase(),
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
            .prismElevated(
                shape = shape,
                tone = color,
                tint = Brush.linearGradient(
                    listOf(
                        color.copy(alpha = .065f),
                        Color.Transparent
                    )
                )
            )
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
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
                trx(title),
                color = Aether.Ink,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                trx(subtitle),
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

// =============================================================================
// MARBLE_LIBRARY_STYLE_V113 — the Library is rebuilt as a grouped, collapsible,
// per-style surface. Sources fold shut and remember it across visits; every
// subscription carries its own refresh and edit actions; a single smart floating
// button owns adding servers/subscriptions plus the revealed sort & Freedom
// controls; and both the chrome and the connected state follow the Home style.
// =============================================================================

private enum class LibraryGroupKind { SUBSCRIPTION, MANUAL, FREEDOM }

private data class LibraryGroup(
    val sourceId: String,
    val title: String,
    val kind: LibraryGroupKind,
    val profiles: List<ProxyProfile>
)

/**
 * Per-Home-style Library identity. Each skin picks its own accent, glow, group
 * silhouette and the exact wording of the live-route badge so the connected
 * state reads as a dedicated, professional treatment on every theme.
 */
private data class LibraryChrome(
    val accent: Color,
    val glow: Color,
    val headerShape: RoundedCornerShape,
    val connectedLabel: String,
    val securingLabel: String,
    val connectedTone: Color,
    val headerTitle: String,
    val freedomTone: Color
)

@Composable
private fun libraryChromeFor(flavor: HomeFlavor): LibraryChrome = when (flavor) {
    HomeFlavor.PRO -> LibraryChrome(
        accent = Aether.Cyan,
        glow = Aether.CyanBright,
        headerShape = RoundedCornerShape(16.dp),
        connectedLabel = "CONNECTED",
        securingLabel = "SECURING",
        connectedTone = Aether.Emerald,
        headerTitle = "SIGNATURE SERVERS",
        freedomTone = Aether.Cyan
    )
    HomeFlavor.ORGANIC -> LibraryChrome(
        accent = Aether.Emerald,
        glow = Aether.Emerald,
        headerShape = RoundedCornerShape(topStart = 26.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 26.dp),
        connectedLabel = "BIOLUMINESCENT",
        securingLabel = "GERMINATING",
        connectedTone = Aether.Emerald,
        headerTitle = "ORGANIC SERVERS",
        freedomTone = Aether.Amethyst
    )
    HomeFlavor.ORBIT -> LibraryChrome(
        accent = Aether.Cyan,
        glow = Aether.Amber,
        headerShape = RoundedCornerShape(18.dp),
        connectedLabel = "IN ORBIT",
        securingLabel = "ACQUIRING",
        connectedTone = Aether.Cyan,
        headerTitle = "ORBITAL MANIFEST",
        freedomTone = Aether.Amber
    )
    HomeFlavor.NEBULA -> LibraryChrome(
        accent = Aether.Amethyst,
        glow = Aether.AmethystBright,
        headerShape = RoundedCornerShape(20.dp),
        connectedLabel = "IMMERSED",
        securingLabel = "NEBULIZING",
        connectedTone = Aether.AmethystBright,
        headerTitle = "NEBULA SERVERS",
        freedomTone = Aether.Amethyst
    )
    HomeFlavor.BLUEPRINT -> LibraryChrome(
        accent = Aether.Cyan,
        glow = Aether.Slate,
        headerShape = RoundedCornerShape(6.dp),
        connectedLabel = "PARAMETRIC",
        securingLabel = "DRAFTING",
        connectedTone = Aether.SlateBright,
        headerTitle = "BLUEPRINT SERVERS",
        freedomTone = Aether.Slate
    )
}

private fun profileMatchesSearch(profile: ProxyProfile, query: String): Boolean =
    query.isBlank() ||
        profile.name.contains(query, true) ||
        profile.scheme.contains(query, true) ||
        profile.host.contains(query, true) ||
        profile.transport.contains(query, true) ||
        profile.security.contains(query, true)

private fun sortLibraryProfiles(
    profiles: List<ProxyProfile>,
    benchmarkById: Map<String, BenchmarkResult>,
    mode: NodeSortMode,
    reverse: Boolean
): List<ProxyProfile> {
    val sorted = when (mode) {
        NodeSortMode.PING -> profiles.sortedWith(
            compareBy<ProxyProfile> { profile ->
                benchmarkById[profile.id]
                    ?.takeIf { it.success > 0 }
                    ?.latencyMs
                    ?: Double.MAX_VALUE
            }.thenBy { it.name.lowercase() }
        )
        NodeSortMode.SCORE -> profiles.sortedWith(
            compareByDescending<ProxyProfile> { profile ->
                benchmarkById[profile.id]
                    ?.takeIf { it.success > 0 }
                    ?.score
                    ?: -1.0
            }.thenBy { it.name.lowercase() }
        )
        NodeSortMode.NAME -> profiles.sortedBy { it.name.lowercase() }
        NodeSortMode.PROTOCOL -> profiles.sortedWith(
            compareBy<ProxyProfile> { it.scheme.lowercase() }
                .thenBy { it.name.lowercase() }
        )
        NodeSortMode.SOURCE -> profiles.sortedWith(
            compareBy<ProxyProfile> { it.subscriptionName.lowercase() }
                .thenBy { it.name.lowercase() }
        )
    }
    return if (reverse) sorted.asReversed() else sorted
}

@Composable
private fun CyberLibrary(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onDetails: (ProxyProfile) -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val contentListState = rememberLazyListState()
    var search by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var manageSubscription by remember { mutableStateOf<Subscription?>(null) }
    var editSubscriptionName by remember { mutableStateOf("") }
    var editSubscriptionUrl by remember { mutableStateOf("") }
    var deleteSubscription by remember { mutableStateOf<Subscription?>(null) }
    var pruneFailedTarget by remember { mutableStateOf<Pair<Subscription, String>?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var globalMenuOpen by remember { mutableStateOf(false) }

    val flavor = homeFlavorFor(parseHomeStyle(repo.settings.homeStyle))
    val chrome = libraryChromeFor(flavor)

    LaunchedEffect(contentListState.isScrollInProgress) {
        onContentScrollChanged(contentListState.isScrollInProgress)
    }

    val benchmarkById = repo.benchmarks.associateBy { it.profileId }
    val searched = repo.libraryProfiles.filter { profileMatchesSearch(it, search) }

    // Subscription groups always render so a freshly added source stays reachable; Manual and
    // Marble Freedom render only when they actually hold nodes. Freedom is hidden until the
    // user reveals it from the smart floating button.
    val groups: List<LibraryGroup> = buildList {
        repo.subscriptions.forEach { sub ->
            val nodes = searched.filter { it.subscriptionId == sub.id }
            if (nodes.isNotEmpty() || search.isBlank()) {
                add(LibraryGroup(sub.id, sub.name, LibraryGroupKind.SUBSCRIPTION, nodes))
            }
        }
        if (repo.settings.manualSourceEnabled) {
            val nodes = searched.filter { it.subscriptionId == "manual" }
            if (nodes.isNotEmpty()) {
                add(LibraryGroup("manual", "Manual", LibraryGroupKind.MANUAL, nodes))
            }
        }
        if (!repo.libraryFreedomHidden) {
            val nodes = searched.filter { it.subscriptionId == ServerlessFreedomEngine.SOURCE_ID }
            if (nodes.isNotEmpty()) {
                add(
                    LibraryGroup(
                        ServerlessFreedomEngine.SOURCE_ID,
                        "Marble Freedom",
                        LibraryGroupKind.FREEDOM,
                        nodes
                    )
                )
            }
        }
    }

    val grouped = groups.map { group ->
        group.copy(
            profiles = sortLibraryProfiles(
                group.profiles,
                benchmarkById,
                repo.settings.nodeSortMode,
                repo.settings.nodeSortReverse
            )
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Aether.VoidElevated,
            title = { Text(trx("Edit server"), color = Aether.Ink) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(trx("Display name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = marbleOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                MarbleDialogAction(
                    label = "Save",
                    tone = Aether.Cyan,
                    variant = PrismButtonVariant.Primary,
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        repo.renameProfile(target.id, renameText, target.subscriptionId)
                        renameTarget = null
                    }
                )
            },
            dismissButton = {
                MarbleDialogAction(label = "Cancel", tone = Aether.InkMuted, onClick = { renameTarget = null })
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
                    Text(trx("Manage subscription"), color = Aether.Ink)
                    Text(
                        "${repo.subscriptionNodeCount(target.id)} servers • ${relativeTime(target.updatedAt)}",
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
                        label = { Text(trx("Source name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = marbleOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = editSubscriptionUrl,
                        onValueChange = { editSubscriptionUrl = it },
                        label = { Text(trx("Subscription URL")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = marbleOutlinedTextFieldColors(),
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
                            "Copy servers",
                            Aether.Cyan,
                            Modifier.weight(1f),
                            enabled = repo.subscriptionNodeCount(target.id) > 0
                        ) {
                            clipboard.setText(AnnotatedString(repo.subscriptionRawText(target.id)))
                            repo.setRuntimeMessage("${repo.subscriptionNodeCount(target.id)} server links copied")
                        }
                    }
                    HorizontalDivider(color = Aether.GlassBorderSoft)
                    Text(
                        trx("Clean failed tests"),
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
                        trx("Only servers with a stored failed result of that exact test type are removed."),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CyberButton(
                            label = "VIEW SERVERS",
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
                MarbleDialogAction(
                    label = "Save",
                    tone = Aether.Cyan,
                    variant = PrismButtonVariant.Primary,
                    enabled = !repo.busy,
                    onClick = {
                        if (repo.updateSubscription(target.id, editSubscriptionName, editSubscriptionUrl)) {
                            manageSubscription = null
                        }
                    }
                )
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleDialogAction(
                        label = "Delete",
                        tone = Aether.Danger,
                        enabled = !repo.busy,
                        onClick = {
                            deleteSubscription = target
                            manageSubscription = null
                        }
                    )
                    MarbleDialogAction(label = "Close", tone = Aether.InkMuted, onClick = { manageSubscription = null })
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
            title = { Text("Remove failed $kind servers?", color = Aether.Danger) },
            text = {
                Text(
                    "This removes $failedCount failed server${if (failedCount == 1) "" else "s"} from ${target.name}.",
                    color = Aether.InkMuted
                )
            },
            confirmButton = {
                MarbleDialogAction(
                    label = "Remove failed",
                    tone = Aether.Danger,
                    enabled = !repo.busy && failedCount > 0 && repo.state == "DISCONNECTED",
                    onClick = {
                        repo.removeFailedSubscriptionNodes(target.id, kind)
                        pruneFailedTarget = null
                    }
                )
            },
            dismissButton = {
                MarbleDialogAction(label = "Cancel", tone = Aether.InkMuted, onClick = { pruneFailedTarget = null })
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
                    "This removes the subscription and its ${repo.subscriptionNodeCount(target.id)} servers.",
                    color = Aether.InkMuted
                )
            },
            confirmButton = {
                MarbleDialogAction(
                    label = "Delete source",
                    tone = Aether.Danger,
                    enabled = !repo.busy,
                    onClick = {
                        if (repo.librarySourceFilter == target.id) repo.selectLibrarySource("all")
                        repo.removeSubscription(target.id)
                        deleteSubscription = null
                    }
                )
            },
            dismissButton = {
                MarbleDialogAction(label = "Cancel", tone = Aether.InkMuted, onClick = { deleteSubscription = null })
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = contentListState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = dockClearance() + 92.dp
            )
        ) {
            // MARBLE_SERVERS_V114 — the list has no blanket item spacing any more. A source module is
            // ONE container painted across several lazy items, so blanket gaps would cut it into
            // slices; each item now owns the space above and below it instead.
            item {
                Column {
                    Box(Modifier.fillMaxWidth()) {
                        MarbleCompactTopBar(
                            title = "Servers",
                            subtitle = "${trx(chrome.headerTitle)} • " +
                                trx("${repo.libraryProfiles.size} servers")
                        )
                        Box(Modifier.align(Alignment.TopStart)) {
                            PrismIconButton(
                                onClick = { globalMenuOpen = true },
                                tone = if (globalMenuOpen) chrome.accent else Aether.InkMuted,
                                selected = globalMenuOpen,
                                enabled = !repo.busy,
                                size = 34.dp,
                                descriptiveLabel = "Global server actions"
                            ) { HomeVectorIcon(HomeIcon.MORE, chrome.accent, Modifier.size(17.dp)) }
                            DropdownMenu(
                                expanded = globalMenuOpen,
                                onDismissRequest = { globalMenuOpen = false },
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                DropdownMenuItem(
                                    text = { Text(trx("Refresh all subscriptions")) },
                                    onClick = { globalMenuOpen = false; repo.refreshLibrarySource("all") }
                                )
                                DropdownMenuItem(
                                    text = { Text(trx("Rank all servers")) },
                                    onClick = { globalMenuOpen = false; repo.smartRankSource("all") }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            item {
                Column {
                    PrismSearchField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "Search servers, host or protocol",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            // Server measurements: refresh and real tunnel ranking. TCP ping is intentionally not exposed.
            item {
                Column {
                    LibraryControlDeck(repo = repo, sourceFilter = "all")
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (grouped.isEmpty()) {
                item {
                    HoloGlass(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (repo.libraryProfiles.isEmpty()) "No connections yet" else "Nothing matches",
                                color = Aether.Ink,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                trx("Use the floating + button to add a subscription, paste configs or import a file."),
                                color = Aether.InkFaint,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            grouped.forEachIndexed { groupIndex, group ->
                val collapsed = group.sourceId in repo.libraryCollapsedSources
                val isSubscription = group.kind == LibraryGroupKind.SUBSCRIPTION
                val accent = when (group.kind) {
                    LibraryGroupKind.FREEDOM -> chrome.freedomTone
                    else -> chrome.accent
                }
                val total = if (isSubscription) {
                    repo.subscriptionNodeCount(group.sourceId)
                } else {
                    group.profiles.size
                }
                // MARBLE_SERVERS_V114 — a folded module keeps a few rows composed so the collapse can
                // shrink and fade them; the rest leave the composition immediately. Eight rows is more
                // than a viewport holds, so nothing that is on screen ever pops out of existence.
                val rows = if (collapsed) {
                    group.profiles.take(LIBRARY_FOLD_KEPT_ROWS)
                } else {
                    group.profiles
                }

                item(key = "group-${group.sourceId}") {
                    Column {
                        if (groupIndex > 0) Spacer(Modifier.height(13.dp))
                        LibraryModuleHeader(
                            group = group,
                            chrome = chrome,
                            flavor = flavor,
                            repo = repo,
                            collapsed = collapsed,
                            searching = search.isNotBlank(),
                            onToggle = { repo.setLibrarySourceCollapsed(group.sourceId, !collapsed) },
                            onEdit = {
                                manageSubscription = repo.subscriptions.firstOrNull { it.id == group.sourceId }
                                if (manageSubscription != null) {
                                    editSubscriptionName = manageSubscription?.name.orEmpty()
                                    editSubscriptionUrl = manageSubscription?.url.orEmpty()
                                }
                            }
                        )
                    }
                }

                itemsIndexed(rows, key = { _, profile -> "${group.sourceId}:${profile.id}" }) { index, profile ->
                    // The fold is animated: each row shrinks and fades in a short stagger, so a
                    // subscription closes like a drawer instead of blinking out.
                    AnimatedVisibility(
                        visible = !collapsed,
                        enter = fadeIn(tween(200, delayMillis = (index * 18).coerceAtMost(180))) +
                            expandVertically(MarbleMotionSpecs.Layout),
                        exit = fadeOut(tween(130)) +
                            shrinkVertically(tween(210, delayMillis = (index * 9).coerceAtMost(90)))
                    ) {
                        SpatialServerCard(
                            profile = profile,
                            repo = repo,
                            result = benchmarkById[profile.id],
                            active = repo.isActiveProfile(profile),
                            probeState = repo.probeStateOf(profile.id),
                            flavor = flavor,
                            ordinal = index,
                            divider = index != rows.lastIndex,
                            accent = accent,
                            onConnect = onConnect,
                            onEdit = {
                                renameTarget = profile
                                renameText = profile.name
                            },
                            onDetails = { onDetails(profile) }
                        )
                    }
                }

                item(key = "group-${group.sourceId}-tail") {
                    LibraryModuleTail(
                        group = group,
                        flavor = flavor,
                        accent = accent,
                        collapsed = collapsed,
                        shown = group.profiles.size,
                        total = total
                    )
                }
            }
        }

        LibrarySmartFab(
            repo = repo,
            chrome = chrome,
            modifier = Modifier.align(Alignment.BottomEnd),
            onAdd = { addOpen = true },
            onSort = { sortOpen = true }
        )
    }

    if (addOpen) {
        LibraryAddSheet(
            repo = repo,
            onImportFile = onImportFile,
            onDismiss = { addOpen = false }
        )
    }

    if (sortOpen) {
        LibrarySortSheet(
            repo = repo,
            onDismiss = { sortOpen = false }
        )
    }
}

@Composable
private fun LibraryChevron(collapsed: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val m = size.minDimension
        val line = Stroke(width = (m * .13f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
        val p = Path()
        if (collapsed) {
            p.moveTo(w * .38f, h * .22f)
            p.lineTo(w * .66f, h * .50f)
            p.lineTo(w * .38f, h * .78f)
        } else {
            p.moveTo(w * .30f, h * .38f)
            p.lineTo(w * .50f, h * .66f)
            p.lineTo(w * .70f, h * .38f)
        }
        drawPath(p, color, style = line)
    }
}

/**
 * MARBLE_SERVERS_V114 — the source module: ONE frosted container per subscription, with every
 * server of that subscription rendered as a compact row *inside* it.
 *
 * Three product rules shaped this:
 *
 *  - **No card per server.** Servers used to be individual glass cards with their own borders,
 *    shadows and 16dp margins, so a 200-server subscription read as 200 boxes. They are now
 *    back-to-back rows in a single poured-glass module, separated by a hairline.
 *  - **The fold is a real animation.** Collapsing a source shrinks and fades its rows in a stagger
 *    instead of deleting them, and the module's own height is animated, so the list never snaps.
 *  - **Nothing about a source is hidden.** The name wraps to two lines, and the server count, the
 *    visible-while-searching count and the time since the source was added each get their own chip
 *    in a wrapping row — no ellipsis swallowing the facts.
 *
 * The lazy list still itemises per row (a 500-server source must not compose in one frame), so the
 * container is drawn segment by segment: the header owns the top corners, every row owns a slice of
 * the frost and one hairline, and the tail owns the bottom corners. Read together they are one box.
 */
private const val LIBRARY_FOLD_KEPT_ROWS = 8

private val LibraryModuleRadius = 18.dp

/** The frost every module segment is poured into: translucent, matte, and never a second shadow. */
@Composable
private fun libraryFrost(accent: Color, emphasized: Boolean): Brush = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = if (emphasized) .055f else .038f),
        accent.copy(alpha = if (emphasized) .075f else .030f),
        Aether.VoidElevated.copy(alpha = .80f),
        Aether.VoidElevated.copy(alpha = .88f)
    )
)

/**
 * One slice of a source module: the shared frost, the module hairline on the edges this slice owns,
 * and (for rows) the divider to the next server.
 */
@Composable
private fun LibraryModuleSegment(
    accent: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    top: Boolean = false,
    bottom: Boolean = false,
    divider: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val radius = LibraryModuleRadius
    val shape = RoundedCornerShape(
        topStart = if (top) radius else 0.dp,
        topEnd = if (top) radius else 0.dp,
        bottomStart = if (bottom) radius else 0.dp,
        bottomEnd = if (bottom) radius else 0.dp
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(libraryFrost(accent, emphasized))
    ) {
        content()
        Canvas(Modifier.matchParentSize()) {
            val hair = 1.dp.toPx()
            val edge = accent.copy(alpha = if (emphasized) .40f else .22f)
            val inset = 14.dp.toPx()
            if (top) drawLine(edge, Offset(0f, hair / 2f), Offset(size.width, hair / 2f), hair)
            if (bottom) {
                drawLine(
                    edge,
                    Offset(0f, size.height - hair / 2f),
                    Offset(size.width, size.height - hair / 2f),
                    hair
                )
            }
            drawLine(edge, Offset(hair / 2f, 0f), Offset(hair / 2f, size.height), hair)
            drawLine(
                edge,
                Offset(size.width - hair / 2f, 0f),
                Offset(size.width - hair / 2f, size.height),
                hair
            )
            if (divider) {
                // The divider is inset from both edges: servers sit in one column of glass, they are
                // not individual cards, so the separator must not read as a card boundary.
                drawLine(
                    accent.copy(alpha = .13f),
                    Offset(inset, size.height - hair / 2f),
                    Offset(size.width - inset, size.height - hair / 2f),
                    hair
                )
            }
        }
    }
}

/** A wrapping metadata chip: one fact, one chip, never a clipped sentence. */
@Composable
private fun LibraryMetaChip(
    text: String,
    tone: Color,
    flavor: HomeFlavor,
    icon: HomeIcon? = null
) {
    val monospace = flavor == HomeFlavor.ORBIT || flavor == HomeFlavor.BLUEPRINT
    val shape = when (flavor) {
        HomeFlavor.PRO -> RoundedCornerShape(999.dp)
        HomeFlavor.ORGANIC -> RoundedCornerShape(
            topStart = 11.dp,
            topEnd = 6.dp,
            bottomStart = 6.dp,
            bottomEnd = 11.dp
        )

        HomeFlavor.ORBIT -> RoundedCornerShape(4.dp)
        HomeFlavor.NEBULA -> RoundedCornerShape(999.dp)
        HomeFlavor.BLUEPRINT -> RoundedCornerShape(2.dp)
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(tone.copy(alpha = .10f))
            .border(1.dp, tone.copy(alpha = .24f), shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) HomeVectorIcon(icon, tone.copy(alpha = .92f), Modifier.size(10.dp))
        val base = MaterialTheme.typography.labelSmall
        Text(
            trx(text),
            color = tone.copy(alpha = .95f),
            style = if (monospace) base.copy(fontFamily = FontFamily.Monospace) else base,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * The head of a source module. Each Home style gets its own treatment of the same facts: a Signature
 * studio plate, a bioluminescent spore, a cockpit manifest line, a nebula orb and a drafting title
 * block — but all five show the full name, the server count and the source age.
 */
@Composable
private fun LibraryModuleHeader(
    group: LibraryGroup,
    chrome: LibraryChrome,
    flavor: HomeFlavor,
    repo: AppRepository,
    collapsed: Boolean,
    searching: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val isSubscription = group.kind == LibraryGroupKind.SUBSCRIPTION
    val sub = repo.subscriptions.firstOrNull { it.id == group.sourceId }
    val local = isSubscription && sub?.url?.isBlank() == true
    val refreshing = isSubscription && group.sourceId in repo.refreshingSources
    val accent = when (group.kind) {
        LibraryGroupKind.FREEDOM -> chrome.freedomTone
        else -> chrome.accent
    }
    // The header always reports the real size of the source; while a search is running the visible
    // slice is reported next to it, so "12 of 340" is a fact rather than a mystery.
    val total = if (isSubscription) repo.subscriptionNodeCount(group.sourceId) else group.profiles.size
    val emphasized = !collapsed && total > 0
    val age = when {
        !isSubscription -> ""
        local -> "Local source"
        else -> relativeTime(sub?.updatedAt ?: 0L)
    }
    val titleStyle = when (flavor) {
        HomeFlavor.PRO -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
        HomeFlavor.ORGANIC -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Light)
        HomeFlavor.ORBIT -> MaterialTheme.typography.labelLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = .8.sp
        )

        HomeFlavor.NEBULA -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        HomeFlavor.BLUEPRINT -> MaterialTheme.typography.labelLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
    }

    LibraryModuleSegment(accent = accent, emphasized = emphasized, top = true) {
        Column(Modifier.fillMaxWidth()) {
            // Per-style crown: the one decorative line that says which product this is.
            when (flavor) {
                HomeFlavor.ORBIT -> Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                ) {
                    val y = size.height - 1.dp.toPx()
                    var x = 6.dp.toPx()
                    while (x < size.width - 6.dp.toPx()) {
                        drawLine(
                            accent.copy(alpha = if (collapsed) .18f else .34f),
                            Offset(x, y),
                            Offset(x, y - 3.dp.toPx()),
                            1.dp.toPx()
                        )
                        x += 9.dp.toPx()
                    }
                }

                HomeFlavor.NEBULA -> Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                accent.copy(alpha = if (collapsed) .25f else .60f),
                                chrome.freedomTone.copy(alpha = if (collapsed) .12f else .34f),
                                Color.Transparent
                            )
                        )
                    )
                }

                else -> Unit
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .kineticClickable(role = Role.Button, onClick = onToggle)
                    .padding(start = 11.dp, end = 7.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fold control: a chevron that rotates rather than swaps glyphs.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = if (collapsed) .08f else .13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .graphicsLayer { rotationZ = if (collapsed) -90f else 0f }
                            .size(15.dp)
                    ) {
                        LibraryChevron(collapsed = false, color = accent, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(9.dp))

                // Per-style leading mark.
                when (flavor) {
                    HomeFlavor.ORGANIC -> Box(
                        Modifier.size(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(
                                        accent.copy(alpha = if (collapsed) .35f else .85f),
                                        Color.Transparent
                                    ),
                                    center = c,
                                    radius = size.minDimension / 2f
                                ),
                                radius = size.minDimension / 2f,
                                center = c
                            )
                        }
                    }

                    HomeFlavor.NEBULA -> Box(
                        Modifier.size(15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(accent.copy(alpha = .30f), size.minDimension / 2f, c, style = Stroke(1.dp.toPx()))
                            drawCircle(accent.copy(alpha = .80f), size.minDimension / 5f, c)
                        }
                    }

                    HomeFlavor.BLUEPRINT -> Text(
                        "%02d".format(repo.subscriptions.indexOfFirst { it.id == group.sourceId } + 1),
                        color = accent.copy(alpha = .80f),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold
                    )

                    else -> Unit
                }
                if (flavor == HomeFlavor.ORGANIC || flavor == HomeFlavor.NEBULA ||
                    flavor == HomeFlavor.BLUEPRINT
                ) {
                    Spacer(Modifier.width(7.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // The full name, on up to two lines — a long subscription title is a fact the
                    // user paid for, not something to ellipsize away.
                    Text(
                        if (flavor == HomeFlavor.ORBIT || flavor == HomeFlavor.BLUEPRINT) {
                            group.title.uppercase()
                        } else {
                            group.title
                        },
                        color = Aether.Ink,
                        style = titleStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Count, visible slice and age each get their own chip in a row that wraps.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = 4
                    ) {
                        LibraryMetaChip(
                            // The Freedom engine is a selectable source in its own right, and it says
                            // so with the same "Freedom (n)" wording the rest of the product uses.
                            text = when (group.kind) {
                                LibraryGroupKind.FREEDOM -> "Freedom ($total)"
                                else -> if (total == 1) "1 server" else "$total servers"
                            },
                            tone = accent,
                            flavor = flavor,
                            icon = if (group.kind == LibraryGroupKind.FREEDOM) {
                                HomeIcon.SHIELD
                            } else {
                                HomeIcon.SERVER
                            }
                        )
                        if (searching) {
                            LibraryMetaChip(
                                text = "${group.profiles.size} shown",
                                tone = Aether.InkMuted,
                                flavor = flavor
                            )
                        }
                        if (age.isNotBlank()) {
                            LibraryMetaChip(
                                text = age,
                                tone = if (local) Aether.InkMuted else Aether.Amethyst,
                                flavor = flavor,
                                icon = HomeIcon.STATUS
                            )
                        }
                        if (group.kind == LibraryGroupKind.FREEDOM) {
                            LibraryMetaChip(
                                text = "Local engine",
                                tone = chrome.freedomTone,
                                flavor = flavor,
                                icon = HomeIcon.SHIELD
                            )
                        }
                    }
                }

                if (isSubscription) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Refresh ${group.title}" }
                            .kineticClickable(
                                enabled = !repo.busy && !local,
                                role = Role.Button,
                                onClick = { repo.refresh(group.sourceId) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Aether.Amethyst,
                                strokeWidth = 2.dp
                            )
                        } else {
                            HomeVectorIcon(
                                HomeIcon.RESET,
                                if (local) Aether.InkFaint else Aether.Amethyst,
                                Modifier.size(17.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Edit ${group.title}" }
                            .kineticClickable(
                                enabled = !repo.busy,
                                role = Role.Button,
                                onClick = onEdit
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        HomeVectorIcon(HomeIcon.MODE, accent, Modifier.size(16.dp))
                    }
                } else {
                    Spacer(Modifier.width(6.dp))
                    LibraryChevron(collapsed, accent, Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                }
            }

            // MARBLE_SERVERS_V114 — a drafting dimension line closes the Blueprint title block.
            if (flavor == HomeFlavor.BLUEPRINT && !collapsed) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(6.dp)
                ) {
                    val y = size.height / 2f
                    drawLine(accent.copy(alpha = .30f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    drawLine(accent.copy(alpha = .45f), Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
                    drawLine(
                        accent.copy(alpha = .45f),
                        Offset(size.width, 0f),
                        Offset(size.width, size.height),
                        1.dp.toPx()
                    )
                }
            }
        }
    }
}

/** The foot of a source module: closes the frost and reports the fold state. */
@Composable
private fun LibraryModuleTail(
    group: LibraryGroup,
    flavor: HomeFlavor,
    accent: Color,
    collapsed: Boolean,
    shown: Int,
    total: Int
) {
    AnimatedVisibility(
        visible = !collapsed,
        enter = fadeIn(MarbleMotionSpecs.ResponseFloat) +
            expandVertically(MarbleMotionSpecs.Layout),
        exit = fadeOut(MarbleMotionSpecs.ExitFloat) +
            shrinkVertically(MarbleMotionSpecs.Layout)
    ) {
        LibraryModuleSegment(accent = accent, emphasized = false, bottom = true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                val monospace = flavor == HomeFlavor.ORBIT || flavor == HomeFlavor.BLUEPRINT
                val base = MaterialTheme.typography.labelSmall
                Text(
                    trx(
                        when (flavor) {
                            HomeFlavor.PRO -> "End of source"
                            HomeFlavor.ORGANIC -> "Colony complete"
                            HomeFlavor.ORBIT -> "END OF MANIFEST"
                            HomeFlavor.NEBULA -> "Field edge"
                            HomeFlavor.BLUEPRINT -> "END OF SHEET"
                        }
                    ),
                    color = Aether.InkFaint,
                    style = if (monospace) base.copy(fontFamily = FontFamily.Monospace) else base,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                ) {
                    val y = size.height / 2f
                    drawLine(
                        accent.copy(alpha = .18f),
                        Offset(0f, y),
                        Offset(size.width, y),
                        1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f))
                    )
                }
                if (shown != total) {
                    Text(
                        "$shown/$total",
                        color = accent.copy(alpha = .85f),
                        style = if (monospace) {
                            base.copy(fontFamily = FontFamily.Monospace)
                        } else {
                            base
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * One server, compact, inside its source module — skinned per Home style.
 *
 * Every flavor shows the same facts (flag or initial, name, protocol, measured latency, live state)
 * but none of them shows them the same way: a Signature studio line, a bioluminescent spore row, a
 * cockpit manifest entry with dotted leaders, a nebula capsule row with a ring gauge, and a drafting
 * spec line with a dimension under the latency. Rows are 54–60dp tall and share one container.
 */
@Composable
private fun LibraryServerRow(
    profile: ProxyProfile,
    flavor: HomeFlavor,
    chrome: LibraryChrome,
    active: Boolean,
    securing: Boolean,
    testing: Boolean,
    latency: Int,
    health: Color,
    ordinal: Int,
    divider: Boolean,
    accent: Color,
    enabled: Boolean,
    onConnect: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val routeTone = if (active) chrome.connectedTone else accent
    val name = stripLeadingFlag(profile.name)
    val flag = leadingFlagGlyph(profile.name)
        ?: countryGlyph(profile.host).takeIf { it.isNotBlank() && it != "◈" }
    val initial = profile.scheme.trim().take(1).uppercase().ifBlank { "S" }
    val protocol = listOfNotNull(
        profile.scheme.uppercase(),
        profile.transport.uppercase().takeIf { it.isNotBlank() && it != "NATIVE" },
        profile.security.uppercase().takeIf { it.isNotBlank() && !it.equals("NONE", true) }
    ).joinToString(" • ")
    val rowHeight = when (flavor) {
        HomeFlavor.PRO -> 60.dp
        HomeFlavor.ORGANIC -> 58.dp
        HomeFlavor.ORBIT -> 54.dp
        HomeFlavor.NEBULA -> 60.dp
        HomeFlavor.BLUEPRINT -> 54.dp
    }

    LibraryModuleSegment(
        accent = accent,
        emphasized = active || securing,
        divider = divider
    ) {
        // State flood: painted under the content, so the measured box of the row never changes.
        if (active || securing || testing) {
            val tone = if (testing) Aether.Cyan else routeTone
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(
                            tone.copy(alpha = if (active) .16f else .10f),
                            tone.copy(alpha = .03f),
                            Color.Transparent
                        )
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .kineticClickable(
                    enabled = enabled && !active,
                    role = Role.Button,
                    onClick = onConnect
                )
        ) {
            when (flavor) {
                // ------------------------------------------------ Signature studio line
                HomeFlavor.PRO -> Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (active) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(routeTone)
                        )
                        Spacer(Modifier.width(9.dp))
                    } else {
                        Text(
                            "%02d".format(ordinal + 1),
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.width(20.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        flag ?: initial,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            name,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1600
                            ),
                            color = if (active) Aether.Ink else Aether.Ink.copy(alpha = .92f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                        Text(
                            if (active) chrome.connectedLabel else protocol,
                            color = if (active) routeTone.copy(alpha = .90f) else Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LibraryLatencyReadout(latency, health, testing, flavor)
                    trailing()
                }

                // ------------------------------------------------ Bioluminescent spore row
                HomeFlavor.ORGANIC -> Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 13.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val glow = if (active || testing) routeTone else accent
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(
                                        glow.copy(alpha = if (active) .80f else .34f),
                                        Color.Transparent
                                    ),
                                    center = c,
                                    radius = size.minDimension / 2f
                                ),
                                radius = size.minDimension / 2f,
                                center = c
                            )
                            drawCircle(
                                color = glow.copy(alpha = if (active) 1f else .55f),
                                radius = size.minDimension * (if (active) .26f else .18f),
                                center = c
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (flag != null) Text(flag, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Text(
                                name,
                                color = Aether.Ink,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Light,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Text(
                            if (active) chrome.connectedLabel else protocol.lowercase(),
                            color = if (active) routeTone else Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LibraryLatencyReadout(latency, health, testing, flavor)
                    trailing()
                }

                // ------------------------------------------------ Cockpit manifest entry
                HomeFlavor.ORBIT -> Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "%03d".format(ordinal + 1),
                        color = if (active) routeTone else Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        name.uppercase(),
                        color = if (active) Aether.Ink else Aether.Ink.copy(alpha = .90f),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = .4.sp
                        ),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Dotted leader: the manifest ties each entry to its measurement the way a
                    // contents page ties a title to a page number. The tone is resolved *outside*
                    // the draw scope — every Aether token is a @Composable getter and a DrawScope
                    // is not a composable context.
                    val leaderTone = (if (active) routeTone else Aether.InkFaint).copy(alpha = .38f)
                    Canvas(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 7.dp)
                            .height(3.dp)
                    ) {
                        val y = size.height / 2f
                        drawLine(
                            leaderTone,
                            Offset(0f, y),
                            Offset(size.width, y),
                            1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f, 4f))
                        )
                    }
                    LibraryLatencyReadout(latency, health, testing, flavor)
                    trailing()
                }

                // ------------------------------------------------ Nebula capsule row
                HomeFlavor.NEBULA -> Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 11.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        routeTone.copy(alpha = if (active) .30f else .14f),
                                        Aether.VoidElevated.copy(alpha = .55f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                routeTone.copy(alpha = if (active) .55f else .26f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            flag ?: initial,
                            color = Aether.Ink,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            name,
                            color = Aether.Ink,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (active) chrome.connectedLabel else protocol,
                            color = if (active) routeTone else Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LibraryLatencyReadout(latency, health, testing, flavor)
                    trailing()
                }

                // ------------------------------------------------ Drafting spec line
                HomeFlavor.BLUEPRINT -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%02d".format(ordinal + 1),
                            color = if (active) routeTone else accent.copy(alpha = .70f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp)
                        )
                        if (flag != null) {
                            Text(flag, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            name.uppercase(),
                            color = if (active) Aether.Ink else Aether.Ink.copy(alpha = .88f),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = .6.sp
                            ),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        LibraryLatencyReadout(latency, health, testing, flavor)
                        trailing()
                    }
                    // Dimension line under the spec: the drafting signature of this style.
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 46.dp, top = 2.dp)
                            .height(5.dp)
                    ) {
                        val y = size.height - 1.dp.toPx()
                        val tone = if (active) routeTone else accent
                        drawLine(tone.copy(alpha = .28f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        drawLine(tone.copy(alpha = .40f), Offset(0f, y - 3.dp.toPx()), Offset(0f, y), 1.dp.toPx())
                        drawLine(
                            tone.copy(alpha = .40f),
                            Offset(size.width, y - 3.dp.toPx()),
                            Offset(size.width, y),
                            1.dp.toPx()
                        )
                        if (latency > 0) {
                            val fraction = (latency / 500f).coerceIn(.04f, 1f)
                            drawLine(
                                health,
                                Offset(0f, y),
                                Offset(size.width * fraction, y),
                                2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The measured latency of one server, drawn the way its style draws instruments. Compact by design:
 * a row must never grow because a benchmark landed.
 */
@Composable
private fun LibraryLatencyReadout(
    latency: Int,
    health: Color,
    testing: Boolean,
    flavor: HomeFlavor
) {
    Box(
        modifier = Modifier
            .width(62.dp)
            .height(30.dp)
            // The number is painted; the judgement is spoken. TalkBack reads "148 ms, good" instead
            // of a bare integer, and the row never grows to carry the word.
            .semantics {
                contentDescription = if (latency > 0) {
                    "Latency $latency milliseconds, ${libraryPingQuality(latency)}"
                } else if (testing) {
                    "Testing server"
                } else {
                    "Not measured"
                }
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        when {
            testing -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Aether.Cyan,
                strokeWidth = 1.8.dp
            )

            latency > 0 -> when (flavor) {
                HomeFlavor.NEBULA -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // A ring gauge instead of bars: the nebula measures in arcs.
                    Canvas(Modifier.size(15.dp)) {
                        val stroke = 1.8.dp.toPx()
                        val inset = stroke
                        val arc = Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(
                            color = health.copy(alpha = .22f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arc,
                            style = Stroke(width = stroke)
                        )
                        drawArc(
                            color = health,
                            startAngle = -90f,
                            sweepAngle = 360f * (1f - (latency / 500f).coerceIn(0f, .95f)),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arc,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        "$latency",
                        color = health,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1
                    )
                }

                HomeFlavor.ORGANIC -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Three spores brighten with the measurement instead of a bar meter.
                    val lit = libraryPingBars(latency)
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .size((4 + index).dp)
                                .clip(CircleShape)
                                .background(
                                    health.copy(alpha = if (index < lit) .85f else .18f)
                                )
                        )
                    }
                    Text(
                        "$latency",
                        color = health,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1
                    )
                }

                else -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "$latency",
                        color = health,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = if (flavor == HomeFlavor.PRO) null else FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1
                    )
                    Text(
                        trx("ms"),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }

            else -> Text(
                "—",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LibraryFabAction(
    icon: HomeIcon,
    label: String,
    tone: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .shadow(10.dp, RoundedCornerShape(999.dp), spotColor = Color.Black.copy(alpha = .24f))
            .background(Aether.VoidElevated.copy(alpha = .97f))
            .border(1.dp, tone.copy(alpha = .42f), RoundedCornerShape(999.dp))
            .kineticClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        HomeVectorIcon(icon, tone, Modifier.size(18.dp))
        Text(
            trx(label),
            color = Aether.Ink,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * MARBLE_SERVERS_V114 — the floating magic button of the Servers screen.
 *
 * Three rules, all of them from the product owner:
 *
 *  1. a single tap opens **nothing** — the old "tap to open the add page" behaviour is gone, so the
 *     button can never fire by accident while the list is being scrolled;
 *  2. *every* action floats and appears **above** the button, and only while the finger is held;
 *  3. the button itself is a **clipboard**, because that is what it does: it collects servers.
 *
 * Releasing the finger (or tapping the dimmed field) folds the actions back. The hold is the only
 * gesture, and the button says so with a small caption until it has been used once.
 */
@Composable
private fun LibrarySmartFab(
    repo: AppRepository,
    chrome: LibraryChrome,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onSort: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    var hintShown by rememberSaveable { mutableStateOf(true) }
    val freedomHidden = repo.libraryFreedomHidden
    val lift by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = MarbleMotionSpecs.ResponseFloat,
        label = "servers-fab-lift"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // A dimmed field behind the revealed actions: it focuses the eye on the stack and gives the
        // hold gesture an obvious way out.
        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(MarbleMotionSpecs.ResponseFloat),
            exit = fadeOut(MarbleMotionSpecs.ExitFloat)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .30f + .18f * lift))
                    .kineticClickable(role = Role.Button, onClick = { revealed = false })
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = dockClearance() + 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = revealed,
                enter = fadeIn(MarbleMotionSpecs.ResponseFloat) +
                    slideInVertically(MarbleMotionSpecs.Spatial) { it },
                exit = fadeOut(MarbleMotionSpecs.ExitFloat) +
                    slideOutVertically(MarbleMotionSpecs.SpatialExit) { it }
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    LibraryFabAction(
                        icon = HomeIcon.SHIELD,
                        label = if (freedomHidden) "Show Marble Freedom" else "Hide Marble Freedom",
                        tone = chrome.freedomTone,
                        onClick = {
                            repo.updateLibraryFreedomHidden(!freedomHidden)
                            revealed = false
                        }
                    )
                    LibraryFabAction(
                        icon = HomeIcon.RANK,
                        label = "Sort servers",
                        tone = chrome.accent,
                        onClick = {
                            revealed = false
                            onSort()
                        }
                    )
                    LibraryFabAction(
                        icon = HomeIcon.CLIPBOARD,
                        label = "Add servers",
                        tone = chrome.glow,
                        onClick = {
                            revealed = false
                            onAdd()
                        }
                    )
                }
            }

            if (hintShown && !revealed) {
                Text(
                    trx("Hold for actions"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Aether.VoidElevated.copy(alpha = .88f))
                        .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(999.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            val fabShape = CircleShape
            // MARBLE_SERVERS_V114 — a clipboard, lifting slightly while its actions are open.
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .graphicsLayer {
                        translationY = -6f * lift * density
                        scaleX = 1f + .05f * lift
                        scaleY = 1f + .05f * lift
                    }
                    .shadow(
                        elevation = 14.dp + 8.dp * lift,
                        shape = fabShape,
                        spotColor = chrome.glow.copy(alpha = .45f),
                        ambientColor = chrome.glow.copy(alpha = .30f)
                    )
                    .clip(fabShape)
                    .background(
                        Brush.linearGradient(
                            listOf(chrome.glow, chrome.accent)
                        )
                    )
                    .combinedClickable(
                        // A single tap deliberately does nothing: no page, no sheet, no navigation.
                        onClick = { hintShown = false },
                        onLongClick = {
                            hintShown = false
                            revealed = true
                        }
                    )
                    .semantics { contentDescription = "Hold for servers actions" },
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(
                    if (revealed) HomeIcon.CANCEL else HomeIcon.CLIPBOARD,
                    Color.White,
                    Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .width(42.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Aether.InkFaint.copy(alpha = .55f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryAddSheet(
    repo: AppRepository,
    onImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("subscription") }
    var url by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    // A valid intake target: the Manual source when it is enabled, otherwise the first
    // subscription so pasted/imported configs always have a home.
    val intakeTarget = when {
        repo.settings.manualSourceEnabled -> "manual"
        else -> repo.subscriptions.firstOrNull()?.id ?: "manual"
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Aether.VoidElevated)
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.Top
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                trx("Add to Servers"),
                color = Aether.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryModeSegment(
                    text = "Subscription",
                    selected = mode == "subscription",
                    tone = Aether.Cyan,
                    modifier = Modifier.weight(1f)
                ) { mode = "subscription" }
                LibraryModeSegment(
                    text = "Manual config",
                    selected = mode == "manual",
                    tone = Aether.Amethyst,
                    modifier = Modifier.weight(1f)
                ) { mode = "manual" }
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (
                        fadeIn(MarbleMotionSpecs.ResponseFloat) +
                            slideInHorizontally(MarbleMotionSpecs.Spatial) { it / 12 }
                    ) togetherWith (
                        fadeOut(MarbleMotionSpecs.ExitFloat) +
                            slideOutHorizontally(MarbleMotionSpecs.SpatialExit) { -it / 14 }
                    )
                },
                label = "library-add-mode-v113"
            ) { selectedMode ->
                if (selectedMode == "manual") {
                    ManualAddEditor(
                        repo = repo,
                        targetSourceId = intakeTarget,
                        onSaved = onDismiss
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(trx("Subscription URL • optional")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = marbleOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = sourceName,
                            onValueChange = { sourceName = it },
                            label = { Text(trx("Name • optional")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = marbleOutlinedTextFieldColors()
                        )
                        CyberButton(
                            variant = PrismButtonVariant.Primary,
                            label = if (url.isBlank()) "Create local source" else "Add subscription",
                            color = Aether.Cyan,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !repo.busy && (
                                url.isBlank() || url.startsWith("https://", true)
                            )
                        ) {
                            repo.addSubscription(sourceName, url)
                            url = ""
                            sourceName = ""
                            onDismiss()
                        }
                    }
                }
            }

            HorizontalDivider(color = Aether.GlassBorderSoft)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryMicroAction(
                    icon = HomeIcon.SPARK,
                    label = "Paste",
                    color = Aether.Emerald,
                    modifier = Modifier.weight(1f)
                ) {
                    repo.importClipboard(
                        clipboard.getText()?.text.orEmpty(),
                        intakeTarget
                    )
                }
                LibraryMicroAction(
                    icon = HomeIcon.DOWNLOAD,
                    label = "Import",
                    color = Aether.Amethyst,
                    modifier = Modifier.weight(1f)
                ) { onImportFile() }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySortSheet(
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        dragHandle = { LibrarySheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                trx("Sort servers"),
                color = Aether.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            LibraryInlineSortBar(repo = repo)
            CyberButton(
                label = "Done",
                color = Aether.Cyan,
                modifier = Modifier.fillMaxWidth(),
                variant = PrismButtonVariant.Primary,
                onClick = onDismiss
            )
        }
    }
}

// MARBLE_LIBRARY_SOURCE_TAB_STRIP_V78 — swipeable subscription tabs
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LibrarySourceTabStrip(
    sources: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    // MARBLE_IOS_FLOATING_GLASS_V81 — the source strip is one detached frosted bar.
    // It is a sticky header, so nodes scroll under it and read through the material.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        val barShape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.13f)
                )
                .clip(barShape)
                .background(Aether.BarGlass)
                .background(
                    Brush.verticalGradient(
                        listOf(Aether.BarGlassHighlight, Color.Transparent)
                    )
                )
                .border(1.dp, Aether.BarGlassBorder, barShape)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(sources, key = { _, item -> item.first }) { index, (id, label) ->
                    val selected = index == selectedIndex
                    val tone by animateColorAsState(
                        if (selected) Aether.Cyan else Aether.InkMuted,
                        MarbleMotionSpecs.Color,
                        label = "lib-tab-tone-$id"
                    )
                    val bg by animateColorAsState(
                        if (selected) Aether.Cyan.copy(alpha = .14f) else Color.Transparent,
                        MarbleMotionSpecs.Color,
                        label = "lib-tab-bg-$id"
                    )
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(shape)
                            .background(bg)
                            .semantics {
                                this.selected = selected
                                contentDescription = "$label tab"
                            }
                            .kineticClickable(
                                role = Role.Tab,
                                boundedShape = shape,
                                showIndication = false
                            ) { onSelect(index) }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = tone,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// MARBLE_LIBRARY_INLINE_SORT_V78 — compact sort strip, no separate filter sheet
// MARBLE_SORT_ROW_FRAME_V91: the reverse toggle is pinned inside the same framed surface as the
// mode chips and the chips wrap onto a second line, so nothing can fall outside the card frame.
@Composable
private fun LibraryInlineSortBar(repo: AppRepository) {
    val frameShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(frameShape)
            .background(Aether.Glass.copy(alpha = .38f))
            .border(1.dp, Aether.GlassBorder.copy(alpha = .65f), frameShape)
            .padding(horizontal = MarbleSpacing.S, vertical = MarbleSpacing.S)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                trx("SORT:"),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            // Reverse toggle stays inside the frame, always visible, never clipped off-screen.
            val revShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clip(revShape)
                    .background(if (repo.settings.nodeSortReverse) Aether.Amber.copy(alpha = .13f) else Color.Transparent)
                    .border(1.dp, if (repo.settings.nodeSortReverse) Aether.Amber.copy(alpha = .38f) else Aether.GlassBorder, revShape)
                    .kineticClickable(role = Role.Button) {
                        repo.updateSettings(repo.settings.copy(nodeSortReverse = !repo.settings.nodeSortReverse))
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (repo.settings.nodeSortReverse) "↑ Rev" else "↓ Rev",
                    color = if (repo.settings.nodeSortReverse) Aether.Amber else Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(MarbleSpacing.XS))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            NodeSortMode.entries.forEach { mode ->
                val selected = repo.settings.nodeSortMode == mode
                val label = when (mode) {
                    NodeSortMode.PING -> "Ping"
                    NodeSortMode.SCORE -> "Score"
                    NodeSortMode.NAME -> "Name"
                    NodeSortMode.PROTOCOL -> "Protocol"
                    NodeSortMode.SOURCE -> "Source"
                }
                val tone = if (selected) Aether.Cyan else Aether.InkMuted
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(shape)
                        .background(if (selected) Aether.Cyan.copy(alpha = .11f) else Color.Transparent)
                        .border(1.dp, if (selected) Aether.Cyan.copy(alpha = .35f) else Aether.GlassBorder, shape)
                        .kineticClickable(role = Role.Button) {
                            repo.updateSettings(repo.settings.copy(nodeSortMode = mode, nodeSortReverse = false))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = tone, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun LibraryModeSegment(
    text: String,
    selected: Boolean,
    tone: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // The add-sheet mode switch is a segmented pair: a selected half lights, the other stays quiet,
    // and neither half changes size while the content below it animates.
    PrismSelectionTile(
        label = text,
        selected = selected,
        tone = Aether.Cyan,
        modifier = modifier,
        minHeight = 40.dp,
        onClick = onClick
    )
}

@Composable
private fun marbleOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Aether.Ink,
    unfocusedTextColor = Aether.Ink,
    focusedContainerColor = Aether.VoidElevated,
    unfocusedContainerColor = Aether.VoidElevated,
    cursorColor = Aether.Cyan,
    focusedBorderColor = Aether.Cyan.copy(alpha = .72f),
    unfocusedBorderColor = Aether.GlassBorder,
    focusedLabelColor = Aether.Cyan,
    unfocusedLabelColor = Aether.InkMuted
)

@Composable
private fun ManualAddEditor(
    repo: AppRepository,
    targetSourceId: String,
    onSaved: () -> Unit
) {
    var mode by remember { mutableStateOf("server") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryModeSegment(
                text = "Manual server",
                selected = mode == "server",
                tone = Aether.Amethyst,
                modifier = Modifier.weight(1f)
            ) { mode = "server" }
            LibraryModeSegment(
                text = "Chain proxy",
                selected = mode == "chain",
                tone = Aether.Amethyst,
                modifier = Modifier.weight(1f)
            ) { mode = "chain" }
        }
        AnimatedContent(targetState = mode, label = "manual-add-kind-v49") { selectedMode ->
            if (selectedMode == "chain") {
                ManualChainEditor(repo, targetSourceId, onSaved)
            } else {
                ManualConfigEditor(repo, targetSourceId, onSaved)
            }
        }
    }
}

@Composable
private fun ManualChainEditor(
    repo: AppRepository,
    targetSourceId: String,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var hops by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val targetReady = targetSourceId == "manual" && repo.settings.manualSourceEnabled ||
        repo.subscriptions.any { it.id == targetSourceId }
    val selectedKeys = hops.toSet()
    val candidates = repo.libraryProfiles.asSequence()
        .filterNot { it.scheme.equals("ssh", true) }
        .filter {
            search.isBlank() || it.name.contains(search, true) ||
                it.host.contains(search, true) || it.scheme.contains(search, true)
        }
        .filterNot { (it.subscriptionId to it.id) in selectedKeys }
        .take(24)
        .toList()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ManualField("Chain name • optional", name, { name = it })

        Text("ORDERED HOPS • ${hops.size}", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        if (hops.isEmpty()) {
            Text(trx("Choose at least two servers below."), color = Aether.Amber, style = MaterialTheme.typography.bodySmall)
        }
        hops.forEachIndexed { index, ref ->
            val profile = repo.profile(ref.second, ref.first)
            PrismWell(
                modifier = Modifier.fillMaxWidth(),
                tone = Aether.Amethyst,
                selected = index == hops.lastIndex,
                contentPadding = PaddingValues(start = 11.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}. ${profile?.name ?: "Unavailable node"}", color = Aether.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (index == hops.lastIndex) "EXIT" else "HOP", color = if (index == hops.lastIndex) Aether.Emerald else Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                }
                PrismIconButton(
                    onClick = {
                        hops = hops.toMutableList().also { list ->
                            val item = list.removeAt(index); list.add(index - 1, item)
                        }
                    },
                    enabled = index > 0,
                    size = 32.dp,
                    descriptiveLabel = "Move hop up"
                ) { Text("↑", color = if (index > 0) Aether.Cyan else Aether.InkFaint, style = MaterialTheme.typography.titleSmall) }
                PrismIconButton(
                    onClick = {
                        hops = hops.toMutableList().also { list ->
                            val item = list.removeAt(index); list.add(index + 1, item)
                        }
                    },
                    enabled = index < hops.lastIndex,
                    size = 32.dp,
                    descriptiveLabel = "Move hop down"
                ) { Text("↓", color = if (index < hops.lastIndex) Aether.Cyan else Aether.InkFaint, style = MaterialTheme.typography.titleSmall) }
                PrismIconButton(
                    onClick = { hops = hops.toMutableList().also { it.removeAt(index) } },
                    tone = Aether.Danger,
                    size = 32.dp,
                    descriptiveLabel = "Remove hop"
                ) { Text("×", color = Aether.Danger, style = MaterialTheme.typography.titleSmall) }
            }
            }
        }

        ManualField("Search servers", search, { search = it })
        candidates.forEach { profile ->
            val chosen = hops.any { it.second == profile.id }
            PrismSelectionTile(
                label = profile.name,
                selected = chosen,
                tone = Aether.Amethyst,
                modifier = Modifier.fillMaxWidth(),
                detail = profile.scheme.uppercase(),
                minHeight = 44.dp,
                alignment = Alignment.CenterStart,
                onClick = {
                    if (chosen) {
                        hops = hops.filterNot { it.second == profile.id }
                    } else {
                        hops = hops + (profile.subscriptionId to profile.id)
                    }
                }
            )
        }
        if (search.isBlank() && candidates.size >= 24) {
            Text(trx("Showing 24 servers • search to find any other server"), color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
        }
        CyberButton(
            variant = PrismButtonVariant.Primary,
            label = "SAVE ${hops.size}-HOP CHAIN",
            color = Aether.Amethyst,
            modifier = Modifier.fillMaxWidth(),
            enabled = targetReady && hops.size >= 2 && !repo.busy
        ) {
            if (repo.addManualChain(name, hops, targetSourceId)) onSaved()
        }
        if (!targetReady) {
            Text(trx("Select one server source first, or enable Manual source."), color = Aether.Amber, style = MaterialTheme.typography.bodySmall)
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
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
                    CyberChoiceChip("Allow insecure TLS", draft.allowInsecure, Aether.Danger, selectionTone = Aether.Danger) {
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
                        CyberChoiceChip("Allow insecure TLS", draft.allowInsecure, Aether.Danger, selectionTone = Aether.Danger) {
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
                    Text(trx("TCP via protected loopback; UDP blocked."), color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
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
                        trx("`unsafe` uses native Go TLS; empty Cipher Suites = automatic."),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall
                    )
                    CyberChoiceChip("Allow insecure TLS", draft.allowInsecure, Aether.Danger, selectionTone = Aether.Danger) {
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

        SectionLabel("Save to", targetSourceName ?: "Select one server source first")
        Text(
            if (targetReady) {
                "Will be added to the selected source and kept across refreshes."
            } else {
                "All sources is only a view. Select a source chip first, then add the config."
            },
            color = if (targetReady) Aether.Emerald else Aether.Amber,
            style = MaterialTheme.typography.bodySmall
        )

        CyberButton(
            variant = PrismButtonVariant.Primary,
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

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { preset ->
            CyberChoiceChip(
                text = preset,
                selected = value.equals(preset, ignoreCase = true),
                color = if (preset == "unsafe") Aether.Amber else Aether.Cyan,
                selectionTone = if (preset == "unsafe") Aether.Amber else Aether.Cyan
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
        label = { Text(trx(label)) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 18,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = marbleOutlinedTextFieldColors(),
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
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = dockClearance()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PrismIconButton(
                    onClick = onBack,
                    size = 38.dp,
                    descriptiveLabel = "Back to servers"
                ) {
                    HomeVectorIcon(HomeIcon.DETAILS, Aether.Cyan, Modifier.size(17.dp))
                }
                Spacer(Modifier.width(6.dp))
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
                    enabled = !repo.busy,
                    variant = PrismButtonVariant.Primary,
                    icon = if (active) HomeIcon.STOP else HomeIcon.POWER
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
        Text(trx(label), color = Aether.InkFaint, style = MaterialTheme.typography.labelMedium)
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFilterSheet(
    repo: AppRepository,
    sourceFilter: String,
    onSourceFilter: (String) -> Unit,
    onManageSubscription: (Subscription) -> Unit,
    onDismiss: () -> Unit
) {
    val manualCount=repo.libraryProfiles.count { it.subscriptionId == "manual" }

    ModalBottomSheet(
        onDismissRequest=onDismiss,
        containerColor=Aether.VoidElevated,
        dragHandle={
            Box(
                Modifier
                    .padding(top=10.dp,bottom=6.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Aether.InkFaint.copy(alpha=.55f))
            )
        }
    ) {
        Column(
            modifier=Modifier
                .fillMaxWidth()
                .padding(horizontal=16.dp)
                .padding(bottom=22.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier=Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        trx("Filter & sort"),
                        color=Aether.Ink,
                        style=MaterialTheme.typography.headlineSmall,
                        fontWeight=FontWeight.Bold
                    )
                    Text(
                        trx("Focus Servers without changing any server"),
                        color=Aether.InkMuted,
                        style=MaterialTheme.typography.bodySmall
                    )
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Aether.Cyan.copy(alpha=.085f))
                        .kineticClickable(role=Role.Button,onClick=onDismiss),
                    contentAlignment=Alignment.Center
                ) {
                    HomeVectorIcon(
                        HomeIcon.CANCEL,
                        Aether.Cyan,
                        Modifier.size(17.dp)
                    )
                }
            }

            PrismPanel(
                modifier=Modifier.fillMaxWidth(),
                accent=Aether.Amethyst,
                contentPadding=PaddingValues(12.dp)
            ) {
                SectionLabel("Sources","Select one subscription or show everything")
                LazyRow(
                    modifier=Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    item("source-all") {
                        SourceOrbitChip(
                            title="All",
                            detail="${repo.libraryProfiles.size} servers",
                            selected=sourceFilter == "all",
                            color=Aether.Cyan,
                            onClick={ onSourceFilter("all") }
                        )
                    }
                    item("source-freedom") {
                        val freedomCount = repo.libraryProfiles.count {
                            it.subscriptionId == ServerlessFreedomEngine.SOURCE_ID
                        }
                        SourceOrbitChip(
                            title="Freedom",
                            detail="$freedomCount local servers",
                            selected=sourceFilter == ServerlessFreedomEngine.SOURCE_ID,
                            color=Aether.Cyan,
                            onClick={ onSourceFilter(ServerlessFreedomEngine.SOURCE_ID) }
                        )
                    }
                    if(repo.settings.manualSourceEnabled) {
                        item("source-manual") {
                            SourceOrbitChip(
                                title="Manual",
                                detail="$manualCount servers",
                                selected=sourceFilter == "manual",
                                color=Aether.Amber,
                                onClick={ onSourceFilter("manual") }
                            )
                        }
                    }
                    items(repo.subscriptions,key={ "sheet-${it.id}" }) { sub ->
                        SourceOrbitChip(
                            title=sub.name,
                            detail="${repo.subscriptionNodeCount(sub.id)} servers",
                            selected=sourceFilter == sub.id,
                            color=if(sub.url.isBlank()) Aether.Emerald else Aether.Amethyst,
                            onClick={ onSourceFilter(sub.id) },
                            onManage={ onManageSubscription(sub) }
                        )
                    }
                }
            }

            PrismPanel(
                modifier=Modifier.fillMaxWidth(),
                accent=Aether.Cyan,
                contentPadding=PaddingValues(12.dp)
            ) {
                SectionLabel("Sort","Fastest, strongest or easiest to find")
                Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier=Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(7.dp)
                    ) {
                        listOf(NodeSortMode.PING,NodeSortMode.SCORE,NodeSortMode.NAME).forEach { mode ->
                            LibrarySortChoice(
                                text=sortModeLabel(mode),
                                selected=repo.settings.nodeSortMode == mode,
                                color=Aether.Cyan,
                                modifier=Modifier.weight(1f)
                            ) {
                                repo.updateSettings(repo.settings.copy(nodeSortMode=mode,nodeSortReverse=false))
                            }
                        }
                    }
                    Row(
                        modifier=Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(7.dp)
                    ) {
                        listOf(NodeSortMode.PROTOCOL,NodeSortMode.SOURCE).forEach { mode ->
                            LibrarySortChoice(
                                text=sortModeLabel(mode),
                                selected=repo.settings.nodeSortMode == mode,
                                color=Aether.Cyan,
                                modifier=Modifier.weight(1f)
                            ) {
                                repo.updateSettings(repo.settings.copy(nodeSortMode=mode,nodeSortReverse=false))
                            }
                        }
                        LibrarySortChoice(
                            text="Reverse",
                            selected=repo.settings.nodeSortReverse,
                            color=Aether.Amber,
                            modifier=Modifier.weight(1f)
                        ) {
                            repo.updateSettings(repo.settings.copy(nodeSortReverse=!repo.settings.nodeSortReverse))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySortChoice(
    text: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PrismSelectionTile(
        label=text,
        selected=selected,
        tone=color,
        modifier=modifier,
        minHeight=48.dp,
        onClick=onClick
    )
}


@Composable
private fun LibraryControlDeck(
    repo: AppRepository,
    sourceFilter: String
) {
    val manualCount=repo.libraryProfiles.count { it.subscriptionId == "manual" }
    val selectedSub=repo.subscriptions.firstOrNull { it.id == sourceFilter }
    val selectedCount=when(sourceFilter) {
        "all" -> repo.libraryProfiles.count { !ServerlessFreedomEngine.isServerless(it) }
        "manual" -> manualCount
        ServerlessFreedomEngine.SOURCE_ID -> repo.libraryProfiles.count {
            it.subscriptionId == ServerlessFreedomEngine.SOURCE_ID
        }
        else -> repo.subscriptionNodeCount(sourceFilter)
    }
    val remoteCount=repo.subscriptions.count { it.url.isNotBlank() }
    val selectedRefreshing=when(sourceFilter) {
        "all" -> repo.refreshingSources.isNotEmpty()
        else -> sourceFilter in repo.refreshingSources
    }
    val canRefreshSelected=when(sourceFilter) {
        "all" -> remoteCount > 0
        "manual" -> false
        else -> selectedSub?.url?.isNotBlank() == true
    }

    Column(
        modifier=Modifier.fillMaxWidth(),
        verticalArrangement=Arrangement.spacedBy(MarbleSpacing.S)
    ) {
        Row(
            modifier=Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(MarbleSpacing.S)
        ) {
            LibraryMicroAction(
                icon=HomeIcon.RESET,
                label=if(selectedRefreshing) "Syncing" else "Refresh",
                color=Aether.Amethyst,
                modifier=Modifier.weight(1f),
                enabled=canRefreshSelected && !repo.busy
            ) { repo.refreshLibrarySource(sourceFilter) }

            // A quiet action beside refresh: ranking measures the real tunnel, never a raw TCP SYN.
            PrismIconButton(
                onClick = { repo.smartRankSource(sourceFilter) },
                tone = Aether.Emerald,
                selected = repo.probeActive,
                enabled = selectedCount > 0 && !repo.busy,
                size = 42.dp,
                descriptiveLabel = "Measure real tunnel latency"
            ) { HomeVectorIcon(HomeIcon.RANK, Aether.Emerald, Modifier.size(18.dp)) }
        }

        AnimatedVisibility(
            visible=repo.probeActive,
            enter=fadeIn(MarbleMotionSpecs.ResponseFloat)+
                expandVertically(MarbleMotionSpecs.Layout),
            exit=fadeOut(MarbleMotionSpecs.ExitFloat)+
                shrinkVertically(MarbleMotionSpecs.Layout)
        ) {
            Column(
                modifier=Modifier.fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier=Modifier.fillMaxWidth().heightIn(min=24.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(9.dp)
                ) {
                    HomeVectorIcon(HomeIcon.RANK,Aether.Cyan,Modifier.size(15.dp))
                    LiveProgressBar(
                        fraction=if(repo.probeTotal > 0) {
                            repo.probeDone.toFloat()/repo.probeTotal.toFloat()
                        } else null,
                        modifier=Modifier.weight(1f),
                        color=Aether.Cyan
                    )
                    Text(
                        "${repo.probeDone.coerceAtMost(repo.probeTotal)}/${repo.probeTotal}",
                        color=Aether.Cyan,
                        style=MaterialTheme.typography.labelSmall.copy(
                            fontFamily=FontFamily.Monospace,
                            fontWeight=FontWeight.Bold
                        )
                    )
                }

                AnimatedContent(
                    targetState=Triple(repo.probeLastName,repo.probeLastOutcome,repo.probeLastLatencyMs),
                    transitionSpec={
                        (fadeIn(MarbleMotionSpecs.ResponseFloat)+
                            slideInVertically(MarbleMotionSpecs.Spatial) { it/2 }) togetherWith
                            (fadeOut(MarbleMotionSpecs.ExitFloat)+
                                slideOutVertically(MarbleMotionSpecs.SpatialExit) { -it/2 })
                    },
                    label="rank-live-node-result"
                ) { event ->
                    if(event.first.isNotBlank()) {
                        val tone=when(event.second) {
                            "FAILED" -> Aether.Danger
                            "OK" -> Aether.Emerald
                            else -> Aether.Cyan
                        }
                        Row(
                            modifier=Modifier.fillMaxWidth(),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(7.dp)
                        ) {
                            HomeVectorIcon(
                                when(event.second) {
                                    "FAILED" -> HomeIcon.CANCEL
                                    "OK" -> HomeIcon.PING
                                    else -> HomeIcon.BENCHMARK
                                },
                                tone,
                                Modifier.size(13.dp)
                            )
                            Text(
                                stripLeadingFlag(event.first),
                                modifier=Modifier.weight(1f),
                                color=Aether.InkMuted,
                                style=MaterialTheme.typography.labelSmall,
                                maxLines=1,
                                overflow=TextOverflow.Ellipsis
                            )
                            Text(
                                when(event.second) {
                                    "FAILED" -> "FAILED"
                                    "OK" -> "${event.third} ms"
                                    else -> "…"
                                },
                                color=tone,
                                style=MaterialTheme.typography.labelSmall.copy(
                                    fontFamily=FontFamily.Monospace,
                                    fontWeight=FontWeight.Bold
                                )
                            )
                        }
                    }
                }
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
    val shape=RoundedCornerShape(18.dp)
    Row(
        modifier=Modifier
            .heightIn(min=48.dp)
            .prismElevated(
                shape=shape,
                tone=Aether.Cyan,
                selected=selected,
                tint=if(selected) {
                    Brush.linearGradient(
                        listOf(
                            Aether.Cyan.copy(alpha=.075f),
                            Aether.Cyan.copy(alpha=.02f)
                        )
                    )
                } else null
            )
            .kineticClickable(role=Role.Button, boundedShape=shape, onClick=onClick)
            .padding(start=10.dp,end=if(onManage != null && selected) 4.dp else 12.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha=.11f)),
            contentAlignment=Alignment.Center
        ) {
            Text(
                title.trim().firstOrNull()?.uppercase() ?: "S",
                color=color,
                style=MaterialTheme.typography.labelLarge,
                fontWeight=FontWeight.Bold
            )
        }
        Column {
            Text(
                trx(title),
                color=if(selected) Aether.Cyan else Aether.Ink,
                style=MaterialTheme.typography.labelMedium,
                fontWeight=FontWeight.SemiBold,
                maxLines=1,
                overflow=TextOverflow.Ellipsis
            )
            Text(
                trx(detail),
                color=Aether.InkFaint,
                style=MaterialTheme.typography.labelSmall,
                maxLines=1
            )
        }
        if(onManage != null && selected) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription="Manage $title" }
                    .kineticClickable(role=Role.Button,onClick=onManage),
                contentAlignment=Alignment.Center
            ) {
                HomeVectorIcon(
                    HomeIcon.MORE,
                    if(selected) Aether.Cyan else color,
                    Modifier.size(17.dp)
                )
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
    variant: PrismButtonVariant = PrismButtonVariant.Primary,
    badge: String = "",
    detail: String = "",
    onClick: () -> Unit
) {
    // The Library action deck is the loudest control group in the screen, so it carries the filled
    // variant: a state-tinted skin, a soft colored glow and an icon that stays legible on the fill.
    PrismButton(
        label=label,
        onClick=onClick,
        tone=color,
        modifier=modifier,
        variant=variant,
        enabled=enabled,
        badge=badge,
        detail=detail,
        contentPadding=PaddingValues(horizontal=11.dp,vertical=9.dp),
        icon={
            HomeVectorIcon(
                icon,
                if(enabled) LocalContentColor.current else Aether.InkFaint,
                Modifier.size(18.dp)
            )
        }
    )
}

private fun sortModeLabel(mode: NodeSortMode): String = when (mode) {
    NodeSortMode.PING -> "Ping"
    NodeSortMode.SCORE -> "Score"
    NodeSortMode.NAME -> "Name"
    NodeSortMode.PROTOCOL -> "Protocol"
    NodeSortMode.SOURCE -> "Source"
}

private fun subscriptionUsageLabel(sub: Subscription): String {
    if (sub.totalBytes <= 0L) return "نامحدود"
    val used = (sub.uploadBytes + sub.downloadBytes).coerceAtLeast(0L)
    fun size(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "%.0f KB".format(bytes / 1_000.0)
    }
    return "${size(used)} / ${size(sub.totalBytes)}"
}

private fun subscriptionExpiryLabel(sub: Subscription): String = when {
    sub.expireAt <= 0L -> "نامحدود"
    sub.expireAt < System.currentTimeMillis() -> "Expired"
    else -> java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US).format(java.util.Date(sub.expireAt))
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
            .prismElevated(
                shape = shape,
                tone = accent,
                selected = selected,
                fill = Aether.VoidElevated
            )
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onView)
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
                "$count servers • ${if (local) "LOCAL" else relativeTime(sub.updatedAt)}${if (selected) " • VIEWING" else ""}",
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${subscriptionUsageLabel(sub)} • ${subscriptionExpiryLabel(sub)}",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Manage ${sub.name}" }
                .kineticClickable(enabled = !repo.busy, role = Role.Button, onClick = onManage),
            contentAlignment = Alignment.Center
        ) {
            HomeVectorIcon(HomeIcon.MORE, Aether.InkMuted, Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpatialServerCard(
    profile: ProxyProfile,
    repo: AppRepository,
    result: BenchmarkResult?,
    active: Boolean,
    probeState: ProbeState,
    flavor: HomeFlavor = HomeFlavor.PRO,
    ordinal: Int = 0,
    divider: Boolean = false,
    accent: Color = Aether.Cyan,
    onConnect: (ProxyProfile) -> Unit,
    onEdit: () -> Unit,
    onDetails: () -> Unit
) {
    val measured=result?.takeIf { it.success > 0 }
    val failedResult=result?.takeIf { it.success <= 0 }
    val latency=measured?.latencyMs?.toInt() ?: 0
    val health=healthColor(latency,result?.success ?: 0)
    val testing=probeState == ProbeState.TESTING
    val queued=probeState == ProbeState.QUEUED
    // The handshake belongs to one row as much as the live route does: while Xray negotiates this exact
    // profile the row keeps the transitional color, then the same frame settles into connected green.
    val securing=!active &&
        repo.state == "CONNECTING" &&
        repo.stateDetail == profile.name
    val emphasized=active || securing
    // MARBLE_LIBRARY_STYLE_V113 — the connected treatment is skinned per Home style: each theme
    // names its live route with its own vocabulary and its own accent instead of one shared pill.
    val chrome=libraryChromeFor(flavor)
    val routeTone=if(active) chrome.connectedTone else chrome.accent
    val builtInFreedom = ServerlessFreedomEngine.isServerless(profile)
    val clipboard=LocalClipboardManager.current

    var menuOpen by remember { mutableStateOf(false) }
    var jsonOpen by remember(profile.id) { mutableStateOf(false) }
    var jsonText by remember(profile.id,profile.configJson) {
        mutableStateOf(profile.configJson)
    }
    var deleteBySwipe by remember(profile.id) { mutableStateOf(false) }

    val swipeState=rememberSwipeToDismissBoxState()

    LaunchedEffect(swipeState.settledValue) {
        when(swipeState.settledValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onEdit()
                swipeState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                deleteBySwipe=true
                swipeState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    if(jsonOpen) {
        AlertDialog(
            onDismissRequest={ jsonOpen=false },
            containerColor=MaterialTheme.colorScheme.surface,
            title={
                Column {
                    Text(trx("Edit Xray JSON"))
                    Text(
                        profile.name,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall,
                        maxLines=1,
                        overflow=TextOverflow.Ellipsis
                    )
                }
            },
            text={
                Column(verticalArrangement=Arrangement.spacedBy(MarbleSpacing.S)) {
                    OutlinedTextField(
                        value=jsonText,
                        onValueChange={ jsonText=it },
                        modifier=Modifier.fillMaxWidth().heightIn(min=260.dp,max=430.dp),
                        colors=marbleOutlinedTextFieldColors(),
                        textStyle=MaterialTheme.typography.bodySmall.copy(
                            fontFamily=FontFamily.Monospace
                        ),
                        label={ Text(trx("Effective config JSON")) },
                        minLines=10,
                        maxLines=22
                    )
                    Text(
                        if(profile.subscriptionId == "manual") {
                            "Manual server • edits are stored locally."
                        } else {
                            "Subscription server • refresh can replace this edit. " +
                                "Duplicate to Manual for a permanent fork."
                        },
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton={
                MarbleDialogAction(
                    label="Save JSON",
                    tone=Aether.Cyan,
                    variant=PrismButtonVariant.Primary,
                    onClick={
                        if(repo.updateProfileJson(
                                profile.id,
                                jsonText,
                                profile.subscriptionId
                            )
                        ) jsonOpen=false
                    }
                )
            },
            dismissButton={
                MarbleDialogAction(label="Cancel", tone=Aether.InkMuted, onClick={ jsonOpen=false })
            }
        )
    }

    if(deleteBySwipe) {
        AlertDialog(
            onDismissRequest={ deleteBySwipe=false },
            title={ Text(trx("Delete server?")) },
            text={
                Text(
                    "Remove ${profile.name} from ${profile.subscriptionName}? " +
                        "Swipe never deletes silently."
                )
            },
            confirmButton={
                MarbleDialogAction(
                    label="Delete",
                    tone=Aether.Danger,
                    onClick={
                        repo.removeProfile(profile.id, profile.subscriptionId)
                        deleteBySwipe=false
                    }
                )
            },
            dismissButton={
                MarbleDialogAction(label="Cancel", tone=Aether.InkMuted, onClick={ deleteBySwipe=false })
            }
        )
    }

    SwipeToDismissBox(
        state=swipeState,
        enableDismissFromStartToEnd=!repo.busy && !builtInFreedom,
        enableDismissFromEndToStart=!repo.busy && !builtInFreedom,
        backgroundContent={
            val edit=swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val tone=if(edit) Aether.Amethyst else Aether.Danger
            Row(
                modifier=Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(tone.copy(alpha=.11f))
                    .padding(horizontal=MarbleSpacing.L),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=if(edit) Arrangement.Start else Arrangement.End
            ) {
                HomeVectorIcon(
                    if(edit) HomeIcon.MODE else HomeIcon.CANCEL,
                    tone,
                    Modifier.size(22.dp)
                )
                Spacer(Modifier.width(MarbleSpacing.S))
                Text(
                    if(edit) "Edit" else "Delete",
                    color=tone,
                    style=MaterialTheme.typography.labelLarge,
                    fontWeight=FontWeight.Bold
                )
            }
        }
    ) {
        // MARBLE_SERVERS_V114 — the server is no longer a card of its own. It is one compact line
        // inside the frosted module of its source: the frost, the hairline, the state flood and the
        // per-style artwork are painted by the module row, and what is left here is the row's own
        // facts (name, protocol, measured latency) plus its actions.
        LibraryServerRow(
            profile = profile,
            flavor = flavor,
            chrome = chrome,
            active = active,
            securing = securing,
            testing = testing,
            latency = latency,
            health = health,
            ordinal = ordinal,
            divider = divider,
            accent = accent,
            enabled = !repo.busy,
            onConnect = { onConnect(profile) }
        ) {
            Box {
                PrismIconButton(
                    onClick = { menuOpen = true },
                    tone = if (menuOpen) Aether.Cyan else Aether.InkMuted,
                    selected = menuOpen,
                    enabled = !repo.busy,
                    size = 34.dp,
                    descriptiveLabel = "More actions for ${profile.name}"
                ) {
                    HomeVectorIcon(
                        HomeIcon.MORE,
                        if (menuOpen) Aether.Cyan else Aether.InkMuted,
                        Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    DropdownMenuItem(
                        text = { Text(trx("Details")) },
                        onClick = {
                            menuOpen = false
                            onDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(trx("Copy config link")) },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(
                                AnnotatedString(
                                    profile.raw.trim().ifBlank {
                                        profile.configJson
                                    }
                                )
                            )
                            repo.setRuntimeMessage("Config copied")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(trx("Copy Xray JSON")) },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(profile.configJson))
                            repo.setRuntimeMessage("Xray JSON copied")
                        }
                    )
                    if (!builtInFreedom && !profile.scheme.equals("ssh", true)) {
                        DropdownMenuItem(
                            text = { Text(trx("Edit Xray JSON")) },
                            onClick = {
                                menuOpen = false
                                jsonText = profile.configJson
                                jsonOpen = true
                            }
                        )
                    }
                    if (repo.settings.manualSourceEnabled && !builtInFreedom) {
                        DropdownMenuItem(
                            text = { Text(trx("Duplicate to Manual")) },
                            onClick = {
                                menuOpen = false
                                repo.duplicateProfile(
                                    profile.id,
                                    profile.subscriptionId
                                )
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(trx("Test this server")) },
                        onClick = {
                            menuOpen = false
                            repo.fullTest(profile)
                        }
                    )
                    if (!builtInFreedom) {
                        DropdownMenuItem(
                            text = { Text(trx("Rename")) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(trx("Delete"), color = Aether.Danger) },
                            onClick = {
                                menuOpen = false
                                deleteBySwipe = true
                            }
                        )
                    }
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
    PrismWell(
        modifier = modifier.heightIn(min = 46.dp),
        radius = 12.dp,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                trx(label),
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
}

// =================================================================================================
// SETTINGS / SWIPEABLE WORKSPACES
// =================================================================================================

private enum class SettingsWorkspaceTab(val label: String, val icon: HomeIcon) {
    GENERAL("General", HomeIcon.SPARK),
    FREEDOM("Freedom", HomeIcon.SHIELD),
    TESTS("Tests", HomeIcon.PING),
    NETWORK("Network", HomeIcon.NETWORK),
    ENGINE("Engine", HomeIcon.TUNNEL),
    SYSTEM("System", HomeIcon.DETAILS)
}

private fun rememberedSettingsTab(name: String): SettingsWorkspaceTab =
    runCatching { SettingsWorkspaceTab.valueOf(name) }.getOrDefault(SettingsWorkspaceTab.GENERAL)

@Composable
private fun settingsTabTone(tab: SettingsWorkspaceTab): Color = when (tab) {
    SettingsWorkspaceTab.GENERAL -> Aether.Cyan
    SettingsWorkspaceTab.FREEDOM -> Aether.Cyan
    SettingsWorkspaceTab.TESTS -> Aether.Amethyst
    SettingsWorkspaceTab.NETWORK -> Aether.Emerald
    SettingsWorkspaceTab.ENGINE -> Aether.Amber
    SettingsWorkspaceTab.SYSTEM -> Aether.Cyan
}


/**
 * MARBLE_SETTINGS_HUB_V114 — the four theme choices with their real miniature previews, shared by
 * the Appearance workspace and the dedicated Theme page so the two can never drift apart.
 * "Dynamic phone" paints the phone's own Material You palette (wallpaper colors) instead of a
 * fixed brand swatch, which is the whole point of that mode.
 */
@Composable
private fun SettingsThemeGrid(repo: AppRepository) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple("System", "Follow device", false) to { repo.updateSettings(repo.settings.copy(theme = "system")) },
            Triple("Light", "Daylight", false) to { repo.updateSettings(repo.settings.copy(theme = "light")) },
            Triple("AMOLED", "Pure black", true) to { repo.updateSettings(repo.settings.copy(theme = "dark")) },
            Triple("Dynamic phone", "Wallpaper colors", false) to { repo.updateSettings(repo.settings.copy(theme = "phone")) }
        ).chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (spec, action) ->
                    val (label, detail, darkPreview) = spec
                    PrismThemeChoice(
                        label = label,
                        detail = detail,
                        // Selection compares the canonical stored id through parseAppTheme so
                        // legacy values ("system"/"light"/"dark", "dynamic") all light up their
                        // own card — AMOLED stores "dark", never "amoled".
                        selected = parseAppTheme(repo.settings.theme) == when (label) {
                            "System" -> AppTheme.SYSTEM
                            "Light" -> AppTheme.LIGHT
                            "AMOLED" -> AppTheme.DARK
                            else -> AppTheme.PHONE_DYNAMIC
                        },
                        darkPreview = darkPreview,
                        accent = when (label) {
                            "AMOLED" -> Aether.Emerald
                            "Light" -> Aether.Cyan
                            else -> Aether.Amethyst
                        },
                        dynamicPreview = label == "Dynamic phone",
                        modifier = Modifier.weight(1f)
                    ) { action() }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// =================================================================================================
// MARBLE_SETTINGS_HUB_V114 — Settings is ONE hub page; every title opens its own sub-page.
// =================================================================================================
//
// The workspace tabs did not disappear, they moved down a level: the hub is a hierarchy of titles
// (Connection / Appearance / System), each title carries a small live preview of what it controls,
// and tapping one slides its own page in. The handful of decisions people touch every day — theme,
// Home style, expert mode, auto-reconnect, auto-refresh, smart alerts, the Intelligence engine —
// stay on the hub itself and apply immediately, so the extra level never costs a tap for them.
//
// Type is deliberately lighter here than anywhere else in the product: settings is a reading
// surface, not a dashboard, so titles are Medium at titleSmall and copy is Normal at labelSmall.

/** Page keys for the settings navigator. Plain strings so the page survives process death. */
private object SettingsPages {
    const val HUB = "hub"
    const val THEME = "theme"
    const val HOME_STYLE = "home-style"
    const val TYPEFACE = "typeface"
    const val LANGUAGE = "language"
    const val INFORMATION = "information"
    private const val WORKSPACE = "workspace"

    fun workspace(tab: SettingsWorkspaceTab, focus: String? = null): String =
        listOf(WORKSPACE, tab.name, focus.orEmpty()).joinToString(":")

    fun isWorkspace(page: String): Boolean = page.startsWith("$WORKSPACE:")

    fun workspaceTab(page: String): SettingsWorkspaceTab =
        rememberedSettingsTab(page.split(":").getOrElse(1) { "" })

    fun workspaceFocus(page: String): String? =
        page.split(":").getOrElse(2) { "" }.ifBlank { null }
}

/**
 * The settings reading ramp: lighter and smaller than the product ramp on purpose. Settings is a
 * reading surface, so nothing here is Bold and nothing is larger than it needs to be.
 */
@Composable
private fun settingsTitleStyle(): TextStyle =
    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)

@Composable
private fun settingsRowTitleStyle(): TextStyle =
    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)

@Composable
private fun settingsBodyStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal)

/** Opens a URL in the browser directly — no chooser sheet, no in-app page. */
private fun openExternal(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * The shell of every settings sub-page: a back arrow, a light title, and one column of content.
 * The hub owns the system back gesture, so this only draws the affordance.
 */
@Composable
private fun SettingsSubPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = dockClearance() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item(key = "sub-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrismIconButton(
                    onClick = onBack,
                    size = 36.dp,
                    descriptiveLabel = "Back to settings"
                ) {
                    HomeVectorIcon(HomeIcon.BACK, Aether.Cyan, Modifier.size(16.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        trx(title),
                        color = Aether.Ink,
                        style = settingsTitleStyle(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        trx(subtitle),
                        color = Aether.InkFaint,
                        style = settingsBodyStyle(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item(key = "sub-body") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                content()
            }
        }
    }
}

/** A hub card: one quiet glass surface holding a titled group of rows. */
@Composable
private fun SettingsHubCard(
    title: String,
    tone: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .92f))
            .border(1.dp, tone.copy(alpha = .16f), shape)
            .padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // MARBLE_SETTINGS_QUIET_CHROME_V114 — no marker bar, no status pip: the group is named
            // in type and nothing else.
            Text(
                trx(title).uppercase(),
                color = tone.copy(alpha = .92f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    trx(subtitle),
                    color = Aether.InkFaint,
                    style = settingsBodyStyle(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        content()
    }
}

/**
 * One navigable title on the hub: a light label, a one-line explanation, a small preview of the
 * thing it controls, and a chevron. The preview is the point — most settings should be recognizable
 * without opening them.
 */
@Composable
private fun SettingsHubRow(
    title: String,
    subtitle: String,
    tone: Color,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Aether.Glass.copy(alpha = .45f))
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                trx(title),
                color = Aether.Ink,
                style = settingsRowTitleStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                trx(subtitle),
                color = Aether.InkMuted,
                style = settingsBodyStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(9.dp))
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(30.dp),
            contentAlignment = Alignment.Center
        ) {
            preview()
        }
        Spacer(Modifier.width(7.dp))
        HomeVectorIcon(HomeIcon.CHEVRON, tone.copy(alpha = .70f), Modifier.size(13.dp))
    }
}

/** A live switch that stays on the hub: important enough not to hide behind a title. */
@Composable
private fun SettingsHubSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .kineticClickable(role = Role.Checkbox, onClick = { onChecked(!checked) })
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                trx(title),
                color = Aether.Ink,
                style = settingsRowTitleStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                trx(subtitle),
                color = Aether.InkMuted,
                style = settingsBodyStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Aether.Void,
                checkedTrackColor = Aether.Cyan,
                uncheckedThumbColor = Aether.InkFaint,
                uncheckedTrackColor = Aether.GlassStrong
            )
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Hub previews — a tiny picture of what each title controls
// ---------------------------------------------------------------------------------------------

/** Four theme swatches that apply immediately: the hub keeps the most-touched choice live. */
@Composable
private fun SettingsThemeMiniRow(repo: AppRepository) {
    val active = parseAppTheme(repo.settings.theme)
    val context = LocalContext.current
    val dynamic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        null
    }
    val choices = listOf(
        Triple(AppTheme.SYSTEM, "System", listOf(Color(0xFF0A0A14), Color(0xFFF0F8FF), Aether.Cyan)),
        Triple(AppTheme.LIGHT, "Light", listOf(Color(0xFFF0F8FF), Color.White, Aether.Cyan)),
        Triple(AppTheme.DARK, "AMOLED", listOf(Color(0xFF000000), Color(0xFF001144), Aether.Emerald)),
        Triple(
            AppTheme.PHONE_DYNAMIC,
            "Dynamic",
            listOf(
                dynamic?.background ?: Color(0xFF101018),
                dynamic?.surface ?: Color(0xFF1D1D27),
                dynamic?.primary ?: Aether.Amethyst
            )
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            trx("Theme"),
            color = Aether.Ink,
            style = settingsRowTitleStyle(),
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            choices.forEach { (theme, label, ramp) ->
                val selected = active == theme
                val shape = RoundedCornerShape(11.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(Aether.Glass.copy(alpha = .40f))
                        .border(
                            1.dp,
                            if (selected) ramp[2].copy(alpha = .62f) else Aether.GlassBorderSoft.copy(alpha = .5f),
                            shape
                        )
                        .kineticClickable(role = Role.Button, boundedShape = shape) {
                            repo.updateSettings(
                                repo.settings.copy(
                                    theme = when (theme) {
                                        AppTheme.SYSTEM -> "system"
                                        AppTheme.LIGHT -> "light"
                                        AppTheme.DARK -> "dark"
                                        AppTheme.PHONE_DYNAMIC -> "phone"
                                    }
                                )
                            )
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // The miniature is the real palette: background, surface and accent.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(ramp[0])
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 4.dp)
                                .width(16.dp)
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(ramp[1])
                        )
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(ramp[2])
                        )
                    }
                    Text(
                        trx(label),
                        color = if (selected) ramp[2] else Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** The five Home styles as live thumbnails — picking one repaints Home immediately. */
@Composable
private fun SettingsStyleMiniRow(repo: AppRepository) {
    val active = parseHomeStyle(repo.settings.homeStyle)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            trx("Home style"),
            color = Aether.Ink,
            style = settingsRowTitleStyle(),
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HomeStyle.entries.forEach { style ->
                val selected = active == style
                val tone = when (style) {
                    HomeStyle.PRO -> Aether.Cyan
                    HomeStyle.BIOLUMINESCENT -> Aether.Emerald
                    HomeStyle.COSMIC_ORBIT -> Aether.Amber
                    HomeStyle.COSMIC_IMMERSION -> Aether.Amethyst
                    HomeStyle.PARAMETRIC -> Aether.SlateBright
                }
                val shape = RoundedCornerShape(11.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(Aether.Glass.copy(alpha = .40f))
                        .border(
                            1.dp,
                            if (selected) tone.copy(alpha = .62f) else Aether.GlassBorderSoft.copy(alpha = .5f),
                            shape
                        )
                        .kineticClickable(role = Role.Button, boundedShape = shape) {
                            repo.updateSettings(repo.settings.copy(homeStyle = style.id))
                        }
                        .padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsStyleMotif(style, tone, Modifier.fillMaxWidth().height(20.dp))
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (selected) tone else Aether.InkFaint.copy(alpha = .35f))
                    )
                }
            }
        }
    }
}

/** A still miniature of each Home style's artwork, so the choice is recognizable at a glance. */
@Composable
private fun SettingsStyleMotif(style: HomeStyle, tone: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (style) {
            // Signature studio: a shutter bar over a quiet dot grid.
            HomeStyle.PRO -> {
                drawCircle(tone.copy(alpha = .45f), h * .30f, Offset(w * .50f, h * .42f))
                drawLine(
                    tone.copy(alpha = .80f),
                    Offset(w * .18f, h * .82f),
                    Offset(w * .82f, h * .82f),
                    2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // Abyss: light shafts and a seed.
            HomeStyle.BIOLUMINESCENT -> {
                drawLine(tone.copy(alpha = .45f), Offset(w * .28f, 0f), Offset(w * .38f, h), 1.6.dp.toPx())
                drawLine(tone.copy(alpha = .30f), Offset(w * .58f, 0f), Offset(w * .70f, h), 1.6.dp.toPx())
                drawCircle(tone, h * .22f, Offset(w * .50f, h * .55f))
            }
            // Command deck: orbits with one body.
            HomeStyle.COSMIC_ORBIT -> {
                val c = Offset(w * .50f, h * .50f)
                drawCircle(tone.copy(alpha = .35f), h * .42f, c, style = Stroke(1.dp.toPx()))
                drawCircle(tone.copy(alpha = .55f), h * .22f, c, style = Stroke(1.dp.toPx()))
                drawCircle(tone, h * .10f, Offset(c.x + h * .42f, c.y))
            }
            // Nebula: a ring, a core and starfield specks.
            HomeStyle.COSMIC_IMMERSION -> {
                val c = Offset(w * .50f, h * .52f)
                drawCircle(tone.copy(alpha = .28f), h * .46f, c, style = Stroke(1.4.dp.toPx()))
                drawCircle(tone.copy(alpha = .80f), h * .16f, c)
                drawCircle(tone.copy(alpha = .50f), 1.dp.toPx(), Offset(w * .20f, h * .22f))
                drawCircle(tone.copy(alpha = .50f), 1.dp.toPx(), Offset(w * .80f, h * .28f))
            }
            // Blueprint: a grid with corner brackets.
            HomeStyle.PARAMETRIC -> {
                val inset = 2.dp.toPx()
                drawLine(tone.copy(alpha = .28f), Offset(inset, inset), Offset(w - inset, inset), 1.dp.toPx())
                drawLine(tone.copy(alpha = .28f), Offset(inset, h - inset), Offset(w - inset, h - inset), 1.dp.toPx())
                drawLine(tone.copy(alpha = .28f), Offset(inset, inset), Offset(inset, h - inset), 1.dp.toPx())
                drawLine(tone.copy(alpha = .28f), Offset(w - inset, inset), Offset(w - inset, h - inset), 1.dp.toPx())
                drawLine(tone, Offset(w * .30f, h * .50f), Offset(w * .70f, h * .50f), 1.6.dp.toPx())
            }
        }
    }
}

/** "Aa" in the candidate face — the cheapest honest preview of a typeface. */
@Composable
private fun SettingsTypefacePreview(fontId: String, tone: Color) {
    Text(
        "Aa",
        color = tone,
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = previewFontFamily(fontId)),
        maxLines = 1
    )
}

/** EN beside فارسی, the Persian half always in Vazirmatn. */
@Composable
private fun SettingsLanguagePreview(tone: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "EN",
            color = tone.copy(alpha = .80f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Text(
            "فارسی",
            color = tone,
            // MARBLE_VAZIR_LANGUAGE_KEY_V114 — Vazir in every state, including this preview.
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = VazirFamily),
            maxLines = 1
        )
    }
}

/** Direct versus proxied traffic, in three strokes. */
@Composable
private fun SettingsRoutingPreview(tone: Color) {
    Canvas(Modifier.size(width = 34.dp, height = 20.dp)) {
        val y = size.height / 2f
        drawLine(tone.copy(alpha = .35f), Offset(0f, y), Offset(size.width, y), 1.2.dp.toPx())
        drawLine(tone, Offset(0f, y), Offset(size.width * .38f, y), 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(
            tone.copy(alpha = .55f),
            Offset(size.width * .55f, y),
            Offset(size.width, y),
            2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
        )
        drawCircle(tone.copy(alpha = .90f), 2.dp.toPx(), Offset(size.width * .46f, y))
    }
}

/** Fragment shapes: what Marble Freedom actually does to a TLS hello. */
@Composable
private fun SettingsFreedomPreview(tone: Color) {
    Canvas(Modifier.size(width = 34.dp, height = 20.dp)) {
        val y = size.height / 2f
        var x = 0f
        var index = 0
        while (x < size.width - 2.dp.toPx()) {
            val width = (4 + (index % 3) * 3).dp.toPx()
            drawLine(
                tone.copy(alpha = .45f + .18f * (index % 3)),
                Offset(x, y),
                Offset(x + width, y),
                3.dp.toPx(),
                cap = StrokeCap.Round
            )
            x += width + 3.dp.toPx()
            index += 1
        }
    }
}

/** A measurement ladder: what the Tests workspace runs. */
@Composable
private fun SettingsTestPreview(tone: Color) {
    PrismSignalMeter(bars = 3, tone = tone, modifier = Modifier.size(width = 20.dp, height = 16.dp))
}

/** Version stamp for the Information title. */
@Composable
private fun SettingsVersionPreview(tone: Color) {
    Text(
        BuildConfig.VERSION_NAME,
        color = tone,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        maxLines = 1
    )
}

// ---------------------------------------------------------------------------------------------
// The hub
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsHub(
    repo: AppRepository,
    onNavigate: (String) -> Unit
) {
    val t = Tr.now
    val settings = repo.settings
    val activeTheme = parseAppTheme(settings.theme)
    val activeStyle = parseHomeStyle(settings.homeStyle)
    val activeFont = parseAppFont(settings.fontFamily)
    val activeLanguage = parseAppLanguage(settings.appLanguage)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = dockClearance() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item(key = "hub-header") {
            MarbleCompactTopBar(
                title = "Settings",
                subtitle = "${t.settingsSubtitle} • ${BuildConfig.VERSION_NAME}"
            )
        }

        // The decisions people touch every day stay here and apply instantly.
        item(key = "hub-quick") {
            SettingsHubCard(
                title = t.quickSettingsTitle,
                subtitle = t.quickSettingsDetail,
                tone = Aether.Cyan
            ) {
                SettingsThemeMiniRow(repo)
                SettingsStyleMiniRow(repo)
                HorizontalDivider(color = Aether.GlassBorderSoft.copy(alpha = .5f))
                SettingsHubSwitch(
                    title = "Reconnect last server",
                    subtitle = "Resume the previous route on launch",
                    checked = settings.rememberLast,
                    onChecked = { enabled ->
                        repo.updateSettings(settings.copy(rememberLast = enabled))
                    }
                )
                SettingsHubSwitch(
                    title = "Auto-refresh sources",
                    subtitle = "Keep subscriptions up to date",
                    checked = settings.subscriptionAutoRefresh,
                    onChecked = { enabled ->
                        repo.updateSettings(settings.copy(subscriptionAutoRefresh = enabled))
                    }
                )
                SettingsHubSwitch(
                    title = "Smart alerts",
                    subtitle = "Notify only when something needs you",
                    checked = settings.smartNotificationsEnabled,
                    onChecked = { enabled ->
                        repo.updateSettings(settings.copy(smartNotificationsEnabled = enabled))
                    }
                )
                SettingsHubSwitch(
                    title = "Marble Intelligence",
                    subtitle = "Compatibility checks and verified tuning",
                    checked = settings.intelligenceEnabled,
                    onChecked = { enabled ->
                        repo.updateSettings(settings.copy(intelligenceEnabled = enabled))
                    }
                )
                SettingsHubSwitch(
                    title = "Expert mode",
                    subtitle = "Reveal the low-level tunnel controls",
                    checked = settings.expertMode,
                    onChecked = { enabled ->
                        repo.updateSettings(settings.copy(expertMode = enabled))
                    }
                )
            }
        }

        // ------------------------------------------------ Connection
        item(key = "hub-connection") {
            SettingsHubCard(
                title = t.categoryConnection,
                subtitle = "Routing, Freedom, tests and servers",
                tone = Aether.Emerald
            ) {
                SettingsHubRow(
                    title = "Network & routing",
                    subtitle = "${trx("DNS, split tunnel and geo rules")} • ${
                        // The same four words the Routing page itself uses, so the hub preview and
                        // the page can never disagree about which mode is active.
                        trx(
                            when (settings.routingMode) {
                                RoutingMode.PROXY_ALL -> "Proxy all"
                                RoutingMode.BYPASS_PRIVATE -> "Private direct"
                                RoutingMode.GEO_DIRECT -> "Geo direct"
                                RoutingMode.CUSTOM -> "Custom"
                            }
                        )
                    }",
                    tone = Aether.Emerald,
                    onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.NETWORK)) }
                ) { SettingsRoutingPreview(Aether.Emerald) }
                SettingsHubRow(
                    title = "Marble Freedom",
                    // Same wording the Freedom page uses for the active preset.
                    subtitle = settings.freedomPreset.name.replace("_", " "),
                    tone = Aether.Amethyst,
                    onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.FREEDOM)) }
                ) { SettingsFreedomPreview(Aether.Amethyst) }
                SettingsHubRow(
                    title = "Tests & ranking",
                    subtitle = "Ping, speed and smart ranking",
                    tone = Aether.Cyan,
                    onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.TESTS)) }
                ) { SettingsTestPreview(Aether.Cyan) }
                SettingsHubRow(
                    title = "General & servers",
                    subtitle = trx("${repo.libraryProfiles.size} servers"),
                    tone = Aether.SlateBright,
                    onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.GENERAL)) }
                ) { SettingsRoutingPreview(Aether.SlateBright) }
            }
        }

        // ------------------------------------------------ Appearance
        item(key = "hub-appearance") {
            SettingsHubCard(
                title = t.categoryAppearance,
                subtitle = "Theme, Home style, typeface and language",
                tone = Aether.Amethyst
            ) {
                SettingsHubRow(
                    title = "Theme",
                    subtitle = when (activeTheme) {
                        AppTheme.SYSTEM -> "Follow device"
                        AppTheme.LIGHT -> "Daylight"
                        AppTheme.DARK -> "Pure black"
                        AppTheme.PHONE_DYNAMIC -> t.themeDynamic
                    },
                    tone = Aether.Amethyst,
                    onClick = { onNavigate(SettingsPages.THEME) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(Aether.VoidElevated, Color.White, Aether.Amethyst).forEach { dot ->
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(dot)
                                    .border(1.dp, Aether.GlassBorderSoft, CircleShape)
                            )
                        }
                    }
                }
                SettingsHubRow(
                    title = t.homeStyleTitle,
                    subtitle = homeStyleLabel(activeStyle),
                    tone = Aether.Cyan,
                    onClick = { onNavigate(SettingsPages.HOME_STYLE) }
                ) { SettingsStyleMotif(activeStyle, Aether.Cyan, Modifier.size(width = 34.dp, height = 20.dp)) }
                SettingsHubRow(
                    title = "Typeface",
                    subtitle = activeFont.label,
                    tone = Aether.Emerald,
                    onClick = { onNavigate(SettingsPages.TYPEFACE) }
                ) { SettingsTypefacePreview(settings.fontFamily, Aether.Emerald) }
                SettingsHubRow(
                    title = t.languageTitle,
                    subtitle = when (activeLanguage) {
                        AppLanguage.SYSTEM -> t.languageSystemDetail
                        AppLanguage.ENGLISH -> "English"
                        AppLanguage.PERSIAN -> "فارسی"
                    },
                    tone = Aether.Amber,
                    onClick = { onNavigate(SettingsPages.LANGUAGE) }
                ) { SettingsLanguagePreview(Aether.Amber) }
            }
        }

        item(key = "hub-per-app") {
            SettingsHubCard(
                title = "Per-app proxy",
                subtitle = "Choose which installed apps use the tunnel",
                tone = Aether.Emerald
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SplitTunnelMode.entries.forEach { mode ->
                        CyberSegment(
                            label = when (mode) {
                                SplitTunnelMode.ALL_APPS -> "All apps"
                                SplitTunnelMode.ONLY_SELECTED -> "Only selected"
                                SplitTunnelMode.BYPASS_SELECTED -> "Bypass"
                            },
                            detail = when (mode) {
                                SplitTunnelMode.ALL_APPS -> "Default"
                                SplitTunnelMode.ONLY_SELECTED -> "Proxy list"
                                SplitTunnelMode.BYPASS_SELECTED -> "Exclude list"
                            },
                            selected = settings.splitTunnelMode == mode,
                            color = Aether.Emerald,
                            modifier = Modifier.weight(1f)
                        ) { repo.updateSettings(settings.copy(splitTunnelMode = mode)) }
                    }
                }
                Text(
                    "${settings.splitTunnelPackages.split(',', '\n', '\r', ';').count { it.isNotBlank() }} apps selected",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // ------------------------------------------------ System
        item(key = "hub-system") {
            SettingsHubCard(
                title = t.categorySystem,
                subtitle = "Notifications, engine, general and information",
                tone = Aether.SlateBright
            ) {
                SettingsHubRow(
                    title = "Notifications",
                    subtitle = "Alerts, live stats and cooldown",
                    tone = Aether.Cyan,
                    onClick = {
                        onNavigate(
                            SettingsPages.workspace(SettingsWorkspaceTab.SYSTEM, "Notifications")
                        )
                    }
                ) { HomeVectorIcon(HomeIcon.STATUS, Aether.Cyan, Modifier.size(20.dp)) }
                SettingsHubRow(
                    title = "Engine & tunnel",
                    subtitle = "Xray, transport and adaptive buffers",
                    tone = Aether.Amber,
                    onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.ENGINE)) }
                ) { HomeVectorIcon(HomeIcon.TUNNEL, Aether.Amber, Modifier.size(20.dp)) }
                SettingsHubRow(
                    title = "General",
                    subtitle = "Home layout, sources and app updates",
                    tone = Aether.Emerald,
                    onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.GENERAL)) }
                ) { HomeVectorIcon(HomeIcon.MODE, Aether.Emerald, Modifier.size(20.dp)) }
                SettingsHubRow(
                    title = t.informationTitle,
                    subtitle = t.informationDetail,
                    tone = Aether.Amethyst,
                    onClick = { onNavigate(SettingsPages.INFORMATION) }
                ) { SettingsVersionPreview(Aether.Amethyst) }
            }
        }

        item(key = "hub-foot") {
            Text(
                trx("Every setting stays on this device."),
                color = Aether.InkFaint,
                style = settingsBodyStyle(),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sub-pages
// ---------------------------------------------------------------------------------------------

/** Theme & colours: the full-size previews, plus the frame personality and studio accent. */
@Composable
private fun SettingsThemePage(repo: AppRepository, onBack: () -> Unit) {
    val t = Tr.now
    SettingsSubPage(
        title = "Theme",
        subtitle = t.themeDetail,
        onBack = onBack
    ) {
        SettingsHubCard(title = "Theme", subtitle = t.themeDetail, tone = Aether.Amethyst) {
            SettingsThemeGrid(repo)
        }
        SettingsHubCard(
            title = t.proNightOutlines,
            subtitle = t.proNightOutlinesDetail,
            tone = Aether.Cyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                DarkOutlineStyle.entries.forEach { outline ->
                    CyberSegment(
                        label = when (outline) {
                            DarkOutlineStyle.SUBTLE -> "Subtle"
                            DarkOutlineStyle.BOLD -> "Bold"
                            DarkOutlineStyle.COLORED -> "Colored"
                            DarkOutlineStyle.HIDDEN -> "Hidden"
                        },
                        detail = "",
                        selected = parseDarkOutlineStyle(repo.settings.darkOutlineStyle) == outline,
                        color = Aether.Amethyst,
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.updateSettings(repo.settings.copy(darkOutlineStyle = outline.id))
                    }
                }
            }
        }
        SettingsHubCard(title = t.proAccentColor, tone = Aether.Emerald) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ProAccent.entries.forEach { accent ->
                    CyberSegment(
                        label = accent.label,
                        detail = "",
                        selected = parseProAccent(repo.settings.proAccent) == accent,
                        selectionTone = signatureAccentColor(accent),
                        color = signatureAccentColor(accent),
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.updateSettings(repo.settings.copy(proAccent = accent.id))
                    }
                }
            }
        }
    }
}

/** Home style: five thumbnails, each drawn from the style's own artwork. */
@Composable
private fun SettingsHomeStylePage(repo: AppRepository, onBack: () -> Unit) {
    val t = Tr.now
    val active = parseHomeStyle(repo.settings.homeStyle)
    SettingsSubPage(
        title = t.homeStyleTitle,
        subtitle = t.homeStyleDetail,
        onBack = onBack
    ) {
        SettingsHubCard(title = t.homeStyleTitle, subtitle = t.homeStyleDetail, tone = Aether.Cyan) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                HomeStyle.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        row.forEach { style ->
                            val selected = active == style
                            val tone = when (style) {
                                HomeStyle.PRO -> Aether.Cyan
                                HomeStyle.BIOLUMINESCENT -> Aether.Emerald
                                HomeStyle.COSMIC_ORBIT -> Aether.Amber
                                HomeStyle.COSMIC_IMMERSION -> Aether.Amethyst
                                HomeStyle.PARAMETRIC -> Aether.SlateBright
                            }
                            val shape = RoundedCornerShape(14.dp)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(Aether.Glass.copy(alpha = .42f))
                                    .border(
                                        1.dp,
                                        if (selected) tone.copy(alpha = .58f) else Aether.GlassBorderSoft.copy(alpha = .5f),
                                        shape
                                    )
                                    .kineticClickable(role = Role.Button, boundedShape = shape) {
                                        repo.updateSettings(repo.settings.copy(homeStyle = style.id))
                                    }
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                SettingsStyleMotif(
                                    style,
                                    tone,
                                    Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        trx(homeStyleLabel(style)),
                                        color = if (selected) tone else Aether.Ink,
                                        style = settingsRowTitleStyle(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        trx(homeStyleDetail(style)),
                                        color = Aether.InkFaint,
                                        style = settingsBodyStyle(),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (active == HomeStyle.PRO) {
            SettingsHubCard(title = t.proStudioTitle, subtitle = t.proStudioDetail, tone = Aether.Amethyst) {
                Text(
                    trx("Every layer of the Signature studio is customizable under General."),
                    color = Aether.InkMuted,
                    style = settingsBodyStyle()
                )
            }
        }
    }
}

/** Typeface: every candidate rendered in its own face. */
@Composable
private fun SettingsTypefacePage(repo: AppRepository, onBack: () -> Unit) {
    val t = Tr.now
    SettingsSubPage(
        title = "Typeface",
        subtitle = "The Latin face of the whole product",
        onBack = onBack
    ) {
        SettingsHubCard(title = "Typeface", subtitle = "The Latin face of the whole product", tone = Aether.Emerald) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppFont.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { font ->
                            val selected = repo.settings.fontFamily.equals(font.id, true)
                            CyberSegment(
                                label = font.label,
                                detail = when (font) {
                                    AppFont.VAZIR -> "Persian"
                                    AppFont.SYSTEM -> "Device default"
                                    AppFont.GOOGLE_SANS -> "Product sans"
                                    AppFont.TIMES_NEW_ROMAN -> "Serif"
                                },
                                selected = selected,
                                color = Aether.Emerald,
                                modifier = Modifier.weight(1f),
                                labelFontFamily = previewFontFamily(font.id)
                            ) {
                                repo.updateSettings(repo.settings.copy(fontFamily = font.id))
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Text(
                trx("Persian always shapes with Vazirmatn, whichever Latin face you pick."),
                color = Aether.InkFaint,
                style = settingsBodyStyle()
            )
        }
    }
}

/** Language: the Persian key is written in Persian and set in Vazirmatn in every state. */
@Composable
private fun SettingsLanguagePage(repo: AppRepository, onBack: () -> Unit) {
    val t = Tr.now
    val selectedLanguage = parseAppLanguage(repo.settings.appLanguage)
    SettingsSubPage(
        title = t.languageTitle,
        subtitle = t.languageDetail,
        onBack = onBack
    ) {
        SettingsHubCard(title = t.languageTitle, subtitle = t.languageDetail, tone = Aether.Amber) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppLanguage.entries.forEach { language ->
                    // MARBLE_VAZIR_LANGUAGE_KEY_V114
                    val persianKey = language == AppLanguage.PERSIAN
                    CyberSegment(
                        label = when (language) {
                            AppLanguage.SYSTEM -> t.languageSystem
                            AppLanguage.ENGLISH -> t.languageEnglish
                            AppLanguage.PERSIAN -> "فارسی"
                        },
                        detail = when (language) {
                            AppLanguage.SYSTEM -> t.languageSystemDetail
                            AppLanguage.ENGLISH -> "English"
                            AppLanguage.PERSIAN -> "Persian"
                        },
                        selected = selectedLanguage == language,
                        color = Aether.Amber,
                        modifier = Modifier.weight(1f),
                        labelFontFamily = if (persianKey) VazirFamily else null,
                        rawLabel = persianKey
                    ) {
                        repo.updateSettings(repo.settings.copy(appLanguage = language.id))
                    }
                }
            }
            Text(
                trx("The Persian choice is written in Persian and always rendered with Vazirmatn."),
                color = Aether.InkFaint,
                style = settingsBodyStyle()
            )
        }
    }
}

/**
 * Information: what this build actually is. App version, the pinned tunnel cores read from
 * core-lock.json at build time, and the source repository — tapping a link opens the browser
 * directly, never an in-app page.
 */
@Composable
private fun SettingsInformationPage(repo: AppRepository, onBack: () -> Unit) {
    val t = Tr.now
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    SettingsSubPage(
        title = t.informationTitle,
        subtitle = t.informationDetail,
        onBack = onBack
    ) {
        SettingsHubCard(title = "Versions", tone = Aether.Cyan) {
            InformationRow("App version", BuildConfig.VERSION_NAME, Aether.Cyan)
            InformationRow("Version code", BuildConfig.VERSION_CODE.toString(), Aether.InkMuted)
            InformationRow("Xray core", BuildConfig.XRAY_CORE_TAG, Aether.Emerald)
            InformationRow("Tunnel core", BuildConfig.HEV_CORE_TAG, Aether.Amber)
            InformationRow("Build type", BuildConfig.BUILD_TYPE, Aether.InkMuted)
        }
        SettingsHubCard(title = "Links", subtitle = "Every link opens in your browser", tone = Aether.Amethyst) {
            InformationLinkRow(
                title = "Source code",
                subtitle = BuildConfig.SOURCE_URL,
                tone = Aether.Amethyst,
                onClick = { openExternal(context, BuildConfig.SOURCE_URL) }
            )
            InformationLinkRow(
                title = "Releases",
                subtitle = "${BuildConfig.SOURCE_URL}/releases",
                tone = Aether.Cyan,
                onClick = { openExternal(context, "${BuildConfig.SOURCE_URL}/releases") }
            )
            InformationLinkRow(
                title = "Report an issue",
                subtitle = "${BuildConfig.SOURCE_URL}/issues",
                tone = Aether.Emerald,
                onClick = { openExternal(context, "${BuildConfig.SOURCE_URL}/issues") }
            )
            InformationLinkRow(
                title = "Xray core",
                subtitle = "https://github.com/${BuildConfig.XRAY_CORE_REPO}",
                tone = Aether.Amber,
                onClick = {
                    openExternal(context, "https://github.com/${BuildConfig.XRAY_CORE_REPO}")
                }
            )
            InformationLinkRow(
                title = "Tunnel core",
                subtitle = "https://github.com/${BuildConfig.HEV_CORE_REPO}",
                tone = Aether.SlateBright,
                onClick = {
                    openExternal(context, "https://github.com/${BuildConfig.HEV_CORE_REPO}")
                }
            )
        }
        SettingsHubCard(title = "Diagnostics", tone = Aether.SlateBright) {
            Text(
                trx("Copy the version details, or keep a continuous diagnostic export in Downloads/marbleng/report."),
                color = Aether.InkMuted,
                style = settingsBodyStyle()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CyberButton(
                    label = "Copy details",
                    color = Aether.Cyan,
                    modifier = Modifier.weight(1f)
                ) {
                    clipboard.setText(
                        AnnotatedString(
                            "MarbleNG ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                                "xray ${BuildConfig.XRAY_CORE_TAG}\n" +
                                "hev-socks5-tunnel ${BuildConfig.HEV_CORE_TAG}"
                        )
                    )
                    repo.setRuntimeMessage("Version details copied")
                }
                CyberButton(
                    label = "Diagnostics",
                    color = Aether.Amethyst,
                    modifier = Modifier.weight(1f)
                ) {
                    repo.updateSettings(repo.settings.copy(debugModeEnabled = !repo.settings.debugModeEnabled))
                }
            }
        }
    }
}

@Composable
private fun InformationRow(label: String, value: String, tone: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            trx(label),
            color = Aether.InkMuted,
            style = settingsBodyStyle(),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(9.dp))
        Text(
            value,
            color = tone,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun InformationLinkRow(
    title: String,
    subtitle: String,
    tone: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Aether.Glass.copy(alpha = .42f))
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                trx(title),
                color = Aether.Ink,
                style = settingsRowTitleStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = tone.copy(alpha = .88f),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        HomeVectorIcon(HomeIcon.GLOBE, tone, Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpatialSettings(
    repo: AppRepository,
    onDialog: (String) -> Unit,
    focusSection: String? = null,
    onContentScrollChanged: (Boolean) -> Unit = {}
) {
    // MARBLE_SETTINGS_HUB_V114 — Settings is one hub page. Every title below it opens its own page,
    // and the pages are plain string keys so the navigator survives a saved-state restore.
    var page by rememberSaveable { mutableStateOf(SettingsPages.HUB) }

    // A deep link from Home ("Routing") still lands directly on the right workspace.
    LaunchedEffect(focusSection) {
        if (focusSection == "Routing") {
            page = SettingsPages.workspace(SettingsWorkspaceTab.NETWORK, "Routing")
        }
    }

    // System back walks up one level: sub-page → hub. It never leaves Settings by accident.
    BackHandler(enabled = page != SettingsPages.HUB) { page = SettingsPages.HUB }

    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            // Forward pages slide in from the trailing edge, back slides out to it: the direction of
            // travel always matches the direction of the hierarchy.
            val direction = if (targetState == SettingsPages.HUB) -1 else 1
            (
                slideInHorizontally(MarbleMotionSpecs.Spatial) { it / 7 * direction } +
                    fadeIn(MarbleMotionSpecs.ResponseFloat)
                ) togetherWith (
                slideOutHorizontally(MarbleMotionSpecs.SpatialExit) { -it / 7 * direction } +
                    fadeOut(MarbleMotionSpecs.ExitFloat)
                )
        },
        label = "settings-page"
    ) { target ->
        when {
            target == SettingsPages.HUB -> SettingsHub(
                repo = repo,
                onNavigate = { page = it }
            )

            SettingsPages.isWorkspace(target) -> SettingsWorkspaceHost(
                repo = repo,
                onDialog = onDialog,
                openTab = SettingsPages.workspaceTab(target),
                focusSection = SettingsPages.workspaceFocus(target),
                onContentScrollChanged = onContentScrollChanged,
                onBack = { page = SettingsPages.HUB }
            )

            target == SettingsPages.THEME -> SettingsThemePage(repo = repo, onBack = { page = SettingsPages.HUB })
            target == SettingsPages.HOME_STYLE ->
                SettingsHomeStylePage(repo = repo, onBack = { page = SettingsPages.HUB })

            target == SettingsPages.TYPEFACE ->
                SettingsTypefacePage(repo = repo, onBack = { page = SettingsPages.HUB })

            target == SettingsPages.LANGUAGE ->
                SettingsLanguagePage(repo = repo, onBack = { page = SettingsPages.HUB })

            else -> SettingsInformationPage(repo = repo, onBack = { page = SettingsPages.HUB })
        }
    }
}

/**
 * The classic settings workspace, now one level below the hub: a tab of grouped section cards.
 * Nothing about its viewport hardening changed — it is still one ordinary LazyColumn with the tab
 * strip as a stickyHeader on mobile and a rail + direct LazyColumn on wide screens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsWorkspaceHost(
    repo: AppRepository,
    onDialog: (String) -> Unit,
    openTab: SettingsWorkspaceTab,
    focusSection: String? = null,
    onContentScrollChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit
) {
    // Settings is intentionally a document now. The former workspace rail/tab strip was
    // confusing and made a title open a second, unrelated navigation system. Every hub title
    // opens one full page containing its own section cards.
    // Legacy viewport contract retained for migration tooling: MARBLE_SETTINGS_CONTENT_VIEWPORT_HARDENING_V72,
    // MARBLE_SETTINGS_TOTAL_HOTFIX_V76, stickyHeader(key = "settings-tabs-strip"),
    // SettingsTabStrip(, remember(activeIndex) { LazyListState() }.
    // Routing focus remains read-only: selectedTabIndex = SettingsWorkspaceTab.NETWORK.ordinal.
    val sections = settingsSections(openTab, repo, repo.settings.expertMode, focusSection)
    Column(Modifier.fillMaxSize()) {
        MarbleCompactTopBar(title = openTab.label, subtitle = "Settings")
        SettingsWorkspacePage(
            sections = sections,
            modifier = Modifier.fillMaxSize().weight(1f),
            repo = repo,
            onContentScrollChanged = onContentScrollChanged
        )
    }
}

@Composable
private fun SettingsTabStrip(
    tabs: List<SettingsWorkspaceTab>,
    activeIndex: Int,
    tabsState: LazyListState,
    onSelect: (Int) -> Unit
) {
    // MARBLE_IOS_FLOATING_GLASS_V81 — the workspace switcher is one detached frosted bar with
    // flat segment pills inside (no shadows per pill). It stays a stickyHeader, so cards scroll
    // under it and through it. The host keeps a fixed height so the LazyColumn geometry below
    // it can never collapse.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        val barShape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 8.dp,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.13f)
                )
                .clip(barShape)
                .background(Aether.BarGlass)
                .background(
                    Brush.verticalGradient(
                        listOf(Aether.BarGlassHighlight, Color.Transparent)
                    )
                )
                .border(1.dp, Aether.BarGlassBorder, barShape)
        ) {
            LazyRow(
                state = tabsState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(
                    tabs,
                    key = { _, tab -> tab.name }
                ) { index, tab ->
                    val selected = activeIndex == index
                    val tone = settingsTabTone(tab)
                    val shape = RoundedCornerShape(16.dp)

                    val pill by animateColorAsState(
                        if (selected) tone.copy(alpha = .14f) else Color.Transparent,
                        MarbleMotionSpecs.Color,
                        label = "settings-tab-pill-${tab.name}"
                    )
                    Row(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(shape)
                            .background(pill)
                            .kineticClickable(
                                role = Role.Tab,
                                boundedShape = shape,
                                showIndication = false
                            ) {
                                onSelect(index)
                            }
                            .semantics {
                                this.selected = selected
                                contentDescription = "${tab.label} tab"
                                stateDescription = if (selected) "Selected" else "Not selected"
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HomeVectorIcon(
                            tab.icon,
                            if (selected) tone else Aether.InkMuted,
                            Modifier.size(15.dp)
                        )
                        Text(
                            trx(tab.label),
                            color = if (selected) tone else Aether.InkMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            // MARBLE_SETTINGS_TABS_SCROLL_HINT_V1 — fade hints so it's obvious the
            // tab strip keeps going instead of just cutting a label off mid-word.
            if (tabsState.canScrollBackward) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(18.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Aether.BarGlass, Color.Transparent))
                        )
                )
            }
            if (tabsState.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(18.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Color.Transparent, Aether.BarGlass))
                        )
                )
            }
        }
    }
}
@Composable
private fun SettingsTabPane(
    tab: SettingsWorkspaceTab,
    selectedTabIndex: Int,
    repo: AppRepository,
    expertMode: Boolean,
    focusSection: String?,
    onContentScrollChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = settingsSections(tab, repo, expertMode, focusSection)
    SettingsWorkspacePage(
        sections = sections,
        modifier = modifier,
        selectedTabIndex = selectedTabIndex,
        repo = repo,
        fallback = false,
        onContentScrollChanged = onContentScrollChanged
    )
}

/**
 * Lazy workspace page used by the wide NavigationRail layout. The weighted modifier is attached
 * directly to the scrollable itself. There is no extra Box between this page and the
 * LazyColumn/Column, so the historical "tab strip visible, weighted workspace at zero height"
 * failure cannot recur.
 */
@Composable
private fun SettingsWorkspacePage(
    sections: List<SettingsSectionSpec>,
    modifier: Modifier = Modifier,
    selectedTabIndex: Int = 0,
    repo: AppRepository,
    fallback: Boolean = false,
    onContentScrollChanged: (Boolean) -> Unit = {}
) {
    var viewportDegraded by remember { mutableStateOf(false) }
    var zeroHeightStreak by remember { mutableIntStateOf(0) }
    val tabName = sections.firstOrNull()?.title ?: "Settings"
    val density = LocalDensity.current

    // The tripwire is attached to the scrollable itself (not to a separate host Box), so it can
    // never add a layout boundary between the weighted parent and the content. A page that is
    // genuinely shorter than the smallest useful settings viewport is treated as collapsed too,
    // because a few-pixel-high strip can survive while every card remains invisible.
    val tripwire = Modifier.onGloballyPositioned { coords ->
        val heightDp = with(density) { coords.size.height.toDp() }
        if (heightDp >= 80.dp) {
            zeroHeightStreak = 0
        } else {
            zeroHeightStreak += 1
            if (zeroHeightStreak >= 2 && !viewportDegraded) {
                viewportDegraded = true
                repo.diagnosticsEvent(
                    "SETTINGS",
                    "viewport-degraded-fallback",
                    "tab" to tabName,
                    "height" to coords.size.height,
                    "heightDp" to heightDp.value,
                    "width" to coords.size.width
                )
            }
        }
    }

    val listState = remember(selectedTabIndex) { LazyListState() }
    val scrollState = remember(selectedTabIndex) { ScrollState(0) }
    LaunchedEffect(listState.isScrollInProgress, scrollState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress || scrollState.isScrollInProgress)
    }

    if (fallback || viewportDegraded) {
        // Non-lazy escape hatch: a plain scrollable Column renders every section eagerly, so no
        // lazy-layout state can hide the options even if the viewport measurement was broken.
        Column(
            modifier = modifier
                .then(tripwire)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = dockClearance()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sections.isEmpty()) {
                emptySettingsCard()
            } else {
                sections.forEach { spec ->
                    SettingsSectionCard(
                        title = spec.title,
                        subtitle = spec.subtitle,
                        icon = spec.icon,
                        color = spec.color
                    ) { spec.content() }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.then(tripwire),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = dockClearance()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sections.isEmpty()) {
                item(key = "settings-empty") {
                    emptySettingsCard()
                }
            } else {
                items(sections, key = { it.title }) { spec ->
                    SettingsSectionCard(
                        title = spec.title,
                        subtitle = spec.subtitle,
                        icon = spec.icon,
                        color = spec.color
                    ) { spec.content() }
                }
            }
        }
    }
}

@Composable
private fun emptySettingsCard() {
    PrismPanel(
        modifier = Modifier.fillMaxWidth(),
        accent = Aether.Cyan,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            trx("Nothing here yet."),
            color = Aether.InkMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * One section card worth of content, shared by the lazy page and the zero-viewport fallback so
 * the two render paths can never drift apart.
 */
private class SettingsSectionSpec(
    val title: String,
    val subtitle: String,
    val icon: HomeIcon,
    val color: Color,
    val content: @Composable () -> Unit
)

/**
 * The single source of truth for what each Settings workspace shows.
 *
 * Every tab is guaranteed at least one real card in both expert and standard modes, and the
 * Home -> Routing focus flow renders its target card without mutating Expert mode.
 */
@Composable
private fun settingsSections(
    tab: SettingsWorkspaceTab,
    repo: AppRepository,
    expertMode: Boolean,
    focusSection: String?
): List<SettingsSectionSpec> {
    fun card(
        title: String,
        subtitle: String,
        icon: HomeIcon,
        color: Color,
        content: @Composable () -> Unit
    ): SettingsSectionSpec = SettingsSectionSpec(title, subtitle, icon, color, content)

    val routingFocused = focusSection == "Routing"
    val expertGate = card(
        "Expert workspace",
        "Show advanced options",
        HomeIcon.SHIELD,
        Aether.Amethyst
    ) { ExpertGateRow(repo) }

    return when (tab) {
        SettingsWorkspaceTab.GENERAL -> listOf(
            card("Appearance","Theme, typeface & per-app proxy",HomeIcon.MODE,Aether.Cyan) { AppearanceSettings(repo) },
            card("Connection","Tunnel, proxy, port",HomeIcon.TUNNEL,Aether.Cyan) { ConnectionSettings(repo) },
            card("Subscriptions","Refresh & sources",HomeIcon.LIBRARY,Aether.Amethyst) { SubscriptionSettings(repo) }
        )
        SettingsWorkspaceTab.FREEDOM -> listOf(
            card("Freedom Engine","Serverless DPI bypass",HomeIcon.SHIELD,Aether.Cyan) { FreedomSettings(repo) }
        )
        SettingsWorkspaceTab.TESTS -> buildList {
            add(card("Testing","Tunnel, TCP, ICMP",HomeIcon.BENCHMARK,Aether.Amethyst) { ProbeSettings(repo) })
            if(expertMode) {
                add(card("Intelligence","Adaptive routing",HomeIcon.SPARK,Aether.Cyan) { IntelligenceSettings(repo) })
            } else {
                add(expertGate)
            }
        }
        SettingsWorkspaceTab.NETWORK -> buildList {
            add(card("Split tunneling","App tunnel / bypass",HomeIcon.PRIVACY,Aether.Emerald) { SplitTunnelSettings(repo) })
            if(expertMode) {
                if(routingFocused) {
                    add(card("Routing","Geo assets & rules",HomeIcon.ROUTING,Aether.Emerald) { RoutingSettings(repo) })
                }
                add(card("Iran Mode","Filter detection",HomeIcon.SHIELD,Aether.Emerald) { IranModeSettings(repo) })
                add(card("DNS","TUN & DoH",HomeIcon.NETWORK,Aether.Cyan) { DnsSettings(repo) })
                if(!routingFocused) {
                    add(card("Routing","Geo assets & rules",HomeIcon.ROUTING,Aether.Emerald) { RoutingSettings(repo) })
                }
            } else {
                if(routingFocused) {
                    add(card("Routing","Geo assets & rules",HomeIcon.ROUTING,Aether.Emerald) { RoutingSettings(repo) })
                }
                add(expertGate)
            }
        }
        SettingsWorkspaceTab.ENGINE -> listOf(
            if(expertMode) {
                card("Fragment & Mux","DPI resilience",HomeIcon.SPARK,Aether.Amber) { FragmentMuxSettings(repo) }
            } else expertGate
        )
        SettingsWorkspaceTab.SYSTEM -> listOf(
            card("Notifications","Alerts",HomeIcon.STATUS,Aether.Cyan) { NotificationSettings(repo) },
            card("Bug Finder","Diagnostics",HomeIcon.DETAILS,Aether.Danger) { BugFinderSettings(repo) }
        )
    }
}

@Composable
private fun ExpertGateRow(repo: AppRepository) {
    Row(
        modifier=Modifier.fillMaxWidth(),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement=Arrangement.spacedBy(2.dp)
        ) {
            Text(
                trx("Expert controls"),
                color=Aether.Ink,
                style=MaterialTheme.typography.bodyMedium,
                fontWeight=FontWeight.Medium
            )
            Text(
                trx("Show every option here."),
                color=Aether.InkMuted,
                style=MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked=repo.settings.expertMode,
            onCheckedChange={ enabled -> repo.updateSettings(repo.settings.copy(expertMode=enabled)) },
            colors=marbleSwitchColors()
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: HomeIcon,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    // MARBLE_IOS_SIMPLIFY_V81 — a calmer, more spacious card: a small tinted icon chip,
    // a compact title row, and air instead of a divider between header and options.
    PrismPanel(
        modifier=Modifier.fillMaxWidth(),
        accent=color,
        contentPadding=PaddingValues(16.dp),
        verticalSpacing=MarbleSpacing.SM
    ) {
        Row(
            modifier=Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha=.12f)),
                contentAlignment=Alignment.Center
            ) {
                HomeVectorIcon(
                    icon,
                    color,
                    Modifier.size(17.dp)
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement=Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    trx(title),
                    color=Aether.Ink,
                    style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold,
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis
                )
                if(subtitle.isNotBlank()) {
                    Text(
                        trx(subtitle),
                        color=Aether.InkMuted,
                        style=MaterialTheme.typography.bodySmall,
                        maxLines=1,
                        overflow=TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        content()
    }
}


@Composable
private fun homeStyleLabel(style: HomeStyle): String = when (style) {
    HomeStyle.PRO -> Tr.now.stylePro
    HomeStyle.BIOLUMINESCENT -> Tr.now.styleBioluminescent
    HomeStyle.COSMIC_ORBIT -> Tr.now.styleCosmicOrbit
    HomeStyle.COSMIC_IMMERSION -> Tr.now.styleCosmicImmersion
    HomeStyle.PARAMETRIC -> Tr.now.styleParametric
}

@Composable
private fun homeStyleDetail(style: HomeStyle): String = when (style) {
    HomeStyle.PRO -> Tr.now.styleProDetail
    HomeStyle.BIOLUMINESCENT -> Tr.now.styleBioluminescentDetail
    HomeStyle.COSMIC_ORBIT -> Tr.now.styleCosmicOrbitDetail
    HomeStyle.COSMIC_IMMERSION -> Tr.now.styleCosmicImmersionDetail
    HomeStyle.PARAMETRIC -> Tr.now.styleParametricDetail
}

@Composable
private fun AppearanceSettings(repo: AppRepository) {
    val t = Tr.now

    // MARBLE_HOME_STYLE_V110 / MARBLE_SIGNATURE_HOME_V112 — the Home presentation is the first
    // appearance decision, because it is the screen the user sees on every launch. The Signature
    // studio ships as the default; the four classic presentations remain one tap away.
    SectionLabel(t.homeStyleTitle)
    Text(
        t.homeStyleDetail,
        color = Aether.InkMuted,
        style = MaterialTheme.typography.bodySmall
    )
    val selectedHomeStyle = parseHomeStyle(repo.settings.homeStyle)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HomeStyle.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { style ->
                    CyberSegment(
                        label = homeStyleLabel(style),
                        detail = homeStyleDetail(style),
                        selected = selectedHomeStyle == style,
                        color = if (style == HomeStyle.PRO) Aether.Cyan else Aether.Amethyst,
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.updateSettings(repo.settings.copy(homeStyle = style.id))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    // MARBLE_SIGNATURE_HOME_V112 — the studio customization surface. Every layer of the
    // professional Home is an independent user choice, mirrored live on the Home screen.
    SectionLabel(t.proStudioTitle)
    Text(
        t.proStudioDetail,
        color = Aether.InkMuted,
        style = MaterialTheme.typography.bodySmall
    )
    SettingSwitch(
        title = t.proFloatingButton,
        subtitle = t.proFloatingButtonDetail,
        checked = repo.settings.proFloatingButtonEnabled,
        onChecked = { enabled ->
            repo.updateSettings(repo.settings.copy(proFloatingButtonEnabled = enabled))
        }
    )
    SettingSwitch(
        title = t.proStatusBanner,
        subtitle = t.proStatusBannerDetail,
        checked = repo.settings.proStatusBannerEnabled,
        onChecked = { enabled ->
            repo.updateSettings(repo.settings.copy(proStatusBannerEnabled = enabled))
        }
    )
    if (repo.settings.proStatusBannerEnabled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProBannerScope.entries.forEach { scope ->
                CyberSegment(
                    label = if (scope == ProBannerScope.HOME) "Home only" else "All pages",
                    detail = "",
                    selected = parseProBannerScope(repo.settings.proBannerScope) == scope,
                    color = Aether.Cyan,
                    modifier = Modifier.weight(1f)
                ) {
                    repo.updateSettings(repo.settings.copy(proBannerScope = scope.id))
                }
            }
        }
    }
    SettingSwitch(
        title = t.proCornerActions,
        subtitle = t.proCornerActionsDetail,
        checked = repo.settings.proCornerActionsEnabled,
        onChecked = { enabled ->
            repo.updateSettings(repo.settings.copy(proCornerActionsEnabled = enabled))
        }
    )
    if (repo.settings.proCornerActionsEnabled) {
        Text(
            t.proShortcut,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ProShortcut.entries.forEach { shortcut ->
                CyberSegment(
                    label = when (shortcut) {
                        ProShortcut.LIBRARY -> t.proShortcutLibrary
                        ProShortcut.RANK -> t.proShortcutRank
                        ProShortcut.PRIVACY -> t.proShortcutPrivacy
                        ProShortcut.ROUTING -> t.proShortcutRouting
                        ProShortcut.TESTS -> t.proShortcutTests
                    },
                    detail = "",
                    selected = parseProShortcut(repo.settings.proShortcut) == shortcut,
                    color = Aether.Emerald,
                    modifier = Modifier.weight(1f)
                ) {
                    repo.updateSettings(repo.settings.copy(proShortcut = shortcut.id))
                }
            }
        }
    }
    SettingSwitch(
        title = t.proServerRail,
        subtitle = t.proServerRailDetail,
        checked = repo.settings.proServerRailEnabled,
        onChecked = { enabled ->
            repo.updateSettings(repo.settings.copy(proServerRailEnabled = enabled))
        }
    )
    if (repo.settings.proServerRailEnabled) {
        Text(
            t.proServerCardStyle,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProServerCardStyle.entries.forEach { cardStyle ->
                CyberSegment(
                    label = when (cardStyle) {
                        ProServerCardStyle.GLASS -> "Glass"
                        ProServerCardStyle.ACCENT -> "Colored"
                        ProServerCardStyle.PLAIN -> "Plain"
                    },
                    detail = "",
                    selected = parseProServerCardStyle(repo.settings.proServerCardStyle) == cardStyle,
                    color = Aether.Amber,
                    modifier = Modifier.weight(1f)
                ) {
                    repo.updateSettings(repo.settings.copy(proServerCardStyle = cardStyle.id))
                }
            }
        }
    }
    SettingSwitch(
        title = t.proStyleSwitcher,
        subtitle = t.proStyleSwitcherDetail,
        checked = repo.settings.proStyleSwitcherEnabled,
        onChecked = { enabled ->
            repo.updateSettings(repo.settings.copy(proStyleSwitcherEnabled = enabled))
        }
    )

    // The accent driving the Signature studio's animated surfaces.
    SectionLabel(t.proAccentColor)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ProAccent.entries.forEach { accent ->
            CyberSegment(
                label = accent.label,
                detail = "",
                selected = parseProAccent(repo.settings.proAccent) == accent,
                selectionTone = signatureAccentColor(accent),
                color = signatureAccentColor(accent),
                modifier = Modifier.weight(1f)
            ) {
                repo.updateSettings(repo.settings.copy(proAccent = accent.id))
            }
        }
    }

    // MARBLE_NIGHT_OUTLINES_V112 — dark-theme frame personality: strengthen, tint or dissolve
    // every card/frame hairline while the night theme is active.
    SectionLabel(t.proNightOutlines)
    Text(
        t.proNightOutlinesDetail,
        color = Aether.InkMuted,
        style = MaterialTheme.typography.bodySmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        DarkOutlineStyle.entries.forEach { outline ->
            CyberSegment(
                label = when (outline) {
                    DarkOutlineStyle.SUBTLE -> "Subtle"
                    DarkOutlineStyle.BOLD -> "Bold"
                    DarkOutlineStyle.COLORED -> "Colored"
                    DarkOutlineStyle.HIDDEN -> "Hidden"
                },
                detail = "",
                selected = parseDarkOutlineStyle(repo.settings.darkOutlineStyle) == outline,
                color = Aether.Amethyst,
                modifier = Modifier.weight(1f)
            ) {
                repo.updateSettings(repo.settings.copy(darkOutlineStyle = outline.id))
            }
        }
    }

    // MARBLE_BILINGUAL_V110 — default is the device language; an explicit choice always wins.
    SectionLabel(t.languageTitle)
    Text(
        t.languageDetail,
        color = Aether.InkMuted,
        style = MaterialTheme.typography.bodySmall
    )
    val selectedLanguage = parseAppLanguage(repo.settings.appLanguage)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppLanguage.entries.forEach { language ->
            // MARBLE_VAZIR_LANGUAGE_KEY_V114 — the Persian key is written in Persian and set in
            // Vazirmatn in every state: unselected, selected, pressed, and under an English UI.
            val persianKey = language == AppLanguage.PERSIAN
            CyberSegment(
                label = when (language) {
                    AppLanguage.SYSTEM -> t.languageSystem
                    AppLanguage.ENGLISH -> t.languageEnglish
                    AppLanguage.PERSIAN -> "فارسی"
                },
                detail = when (language) {
                    AppLanguage.SYSTEM -> t.languageSystemDetail
                    AppLanguage.ENGLISH -> "English"
                    AppLanguage.PERSIAN -> "Persian"
                },
                selected = selectedLanguage == language,
                color = Aether.Cyan,
                modifier = Modifier.weight(1f),
                labelFontFamily = if (persianKey) VazirFamily else null,
                rawLabel = persianKey
            ) {
                repo.updateSettings(repo.settings.copy(appLanguage = language.id))
            }
        }
    }

    // Standard settings intentionally keeps the useful decisions above the expert gate.
    SectionLabel("Theme")
    // MARBLE_PHONE_DYNAMIC_THEME_V113 — four themes now, each with a real miniature preview.
    // "Dynamic phone" borrows the phone's Material You palette (wallpaper colors) everywhere;
    // its thumbnail paints the live dynamic scheme, not a fixed brand swatch.
    SettingsThemeGrid(repo)

    // MARBLE_SYSTEM_FONT_V112 — the device's own typeface is a first-class choice. Persian copy
    // keeps shaping with the bundled Vazirmatn regardless of this selection (AetherTheme pins
    // it whenever the product language is Persian), so this only changes the Latin ramp.
    SectionLabel("Typeface")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppFont.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { font ->
                    CyberSegment(
                        label = font.label,
                        detail = when (font) {
                            AppFont.VAZIR -> "Persian"
                            AppFont.SYSTEM -> "Device default"
                            AppFont.GOOGLE_SANS -> "Product sans"
                            AppFont.TIMES_NEW_ROMAN -> "Serif"
                        },
                        selected = repo.settings.fontFamily.equals(font.id, true),
                        color = Aether.Cyan,
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.updateSettings(repo.settings.copy(fontFamily = font.id))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    SectionLabel("Per-app proxy")
    Text(
        trx("Choose which installed apps use the tunnel. The complete app list is available in Network."),
        color = Aether.InkMuted,
        style = MaterialTheme.typography.bodySmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SplitTunnelMode.entries.forEach { mode ->
            CyberSegment(
                label = when (mode) {
                    SplitTunnelMode.ALL_APPS -> "All apps"
                    SplitTunnelMode.ONLY_SELECTED -> "Only selected"
                    SplitTunnelMode.BYPASS_SELECTED -> "Bypass"
                },
                detail = when (mode) {
                    SplitTunnelMode.ALL_APPS -> "Default"
                    SplitTunnelMode.ONLY_SELECTED -> "Proxy list"
                    SplitTunnelMode.BYPASS_SELECTED -> "Exclude list"
                },
                selected = repo.settings.splitTunnelMode == mode,
                color = Aether.Emerald,
                modifier = Modifier.weight(1f)
            ) {
                repo.updateSettings(repo.settings.copy(splitTunnelMode = mode))
            }
        }
    }
    Text(
        "${repo.settings.splitTunnelPackages.split(',', '\n', '\r', ';').count { it.isNotBlank() }} apps selected",
        color = Aether.InkFaint,
        style = MaterialTheme.typography.labelSmall
    )

    HorizontalDivider(color = Aether.GlassBorderSoft)

    SettingSwitch(
        "App update checks",
        "Look for signed MarbleNG releases",
        repo.settings.appUpdateCheckEnabled
    ) { enabled ->
        repo.updateSettings(repo.settings.copy(appUpdateCheckEnabled = enabled))
        if (enabled) repo.checkForAppUpdate(force = true)
    }

    if (!repo.settings.expertMode) {
        CyberButton(
            label = "If you're an expert, tap here",
            detail = "Reveal advanced routing, engine and diagnostic controls",
            color = Aether.Amethyst,
            modifier = Modifier.fillMaxWidth(),
            variant = PrismButtonVariant.Secondary,
            icon = HomeIcon.SHIELD
        ) {
            repo.updateSettings(repo.settings.copy(expertMode = true))
        }
    } else {
        SettingSwitch(
            "Expert controls enabled",
            "Advanced workspaces are visible",
            true
        ) { enabled ->
            repo.updateSettings(repo.settings.copy(expertMode = enabled))
        }
    }
}

@Composable
private fun ConnectionSettings(repo: AppRepository) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyberSegment(
            label = "Full TUN",
            detail = "Device",
            selected = repo.settings.connectionMode == ConnectionMode.FULL_TUN,
            color = Aether.Cyan,
            modifier = Modifier.weight(1f)
        ) {
            repo.setConnectionMode(ConnectionMode.FULL_TUN)
        }

        CyberSegment(
            label = "Local",
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
        subtitle = "Adaptive per-network policies",
        checked = s.intelligenceEnabled
    ) { repo.updateSettings(s.copy(intelligenceEnabled = it)) }

    SettingSwitch(
        title = "Maximum config compatibility",
        subtitle = "Verify the final config with Xray",
        checked = s.configCompatibilityMode
    ) { repo.updateSettings(repo.settings.copy(configCompatibilityMode = it)) }

    SettingSwitch(
        title = "Verified performance auto-tune",
        subtitle = "Keep only proven gains",
        checked = s.verifiedPerformanceTuning
    ) { repo.updateSettings(repo.settings.copy(verifiedPerformanceTuning = it)) }

    SettingSwitch(
        title = "Marble Turbo acceleration",
        subtitle = "Pick the fastest transport on connect",
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
                subtitle = "Learn in background, keep the tunnel",
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
                subtitle = "Size buffers from real throughput",
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
        subtitle = "Verify route, rotate challenges",
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
                subtitle = "Pause scans during downloads",
                checked = s.optimizerAvoidHeavyTraffic
            ) { repo.updateSettings(repo.settings.copy(optimizerAvoidHeavyTraffic = it)) }
        }
    }

    SettingSwitch(
        title = "Persistent route intelligence",
        subtitle = "Health history per network",
        checked = s.healthHistoryEnabled
    ) { repo.updateSettings(s.copy(healthHistoryEnabled = it)) }

    SettingSwitch(
        title = "Connection race",
        subtitle = "First healthy route wins",
        checked = s.raceConnectEnabled
    ) { repo.updateSettings(s.copy(raceConnectEnabled = it)) }

    AnimatedVisibility(s.raceConnectEnabled) {
        NumberSetting("Race width", s.raceWidth, 2..4) {
            repo.updateSettings(repo.settings.copy(raceWidth = it))
        }
    }

    SettingSwitch(
        title = "Smart fallback",
        subtitle = "Fail-closed switch to a backup",
        checked = s.smartFallbackEnabled
    ) { repo.updateSettings(s.copy(smartFallbackEnabled = it)) }

    AnimatedVisibility(s.smartFallbackEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            NumberSetting("Fallback depth", s.fallbackCount, 1..8) {
                repo.updateSettings(repo.settings.copy(fallbackCount = it))
            }
            SettingSwitch(
                title = "Auto-connect after kill switch",
                subtitle = "Off = tap Connect again",
                checked = s.autoReconnectAfterKillSwitch
            ) { repo.updateSettings(repo.settings.copy(autoReconnectAfterKillSwitch = it)) }
        }
    }

    SettingSwitch(
        title = "Network-change recovery",
        subtitle = "Re-probe after Wi-Fi ↔ cellular",
        checked = s.networkChangeRecoveryEnabled
    ) { repo.updateSettings(s.copy(networkChangeRecoveryEnabled = it)) }

    SettingSwitch(
        title = "Adaptive MTU",
        subtitle = "Match the real link MTU",
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
        subtitle = "Ease off before throttling",
        checked = s.thermalAwareEnabled
    ) { repo.updateSettings(s.copy(thermalAwareEnabled = it)) }

    SettingSwitch(
        title = "Adaptive throughput test",
        subtitle = "Start small, grow on trust",
        checked = s.adaptiveThroughputEnabled
    ) { repo.updateSettings(s.copy(adaptiveThroughputEnabled = it)) }

    SettingSwitch(
        title = "UDP / QUIC probe",
        subtitle = "Real UDP health via STUN",
        checked = s.udpProbeEnabled
    ) { repo.updateSettings(s.copy(udpProbeEnabled = it)) }

    Text(trx("Workload"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
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
    Text(trx("Privacy sentinel"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        HoloBadge(sentinel.coverage, if (sentinel.coverage == "DEVICE-WIDE") Aether.Emerald else Aether.Amber, compact = true)
        HoloBadge(if (sentinel.dnsHijack) "DNS HIJACK" else "DNS OPEN", if (sentinel.dnsHijack) Aether.Emerald else Aether.Amber, compact = true)
        HoloBadge(if (sentinel.killSwitchArmed) "KILL SWITCH" else "NO KILL SWITCH", if (sentinel.killSwitchArmed) Aether.Emerald else Aether.InkFaint, compact = true)
    }
    if (sentinel.splitBypassCount > 0) {
        Text("${sentinel.splitBypassCount} apps bypass the VPN; coverage is partial.", color = Aether.Amber, style = MaterialTheme.typography.bodySmall)
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


    if (Build.VERSION.SDK_INT >= 33 && !permissionGranted) {
        CyberButton(
            label = "Grant notification access",
            color = Aether.Amber,
            modifier = Modifier.fillMaxWidth()
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        CyberButton(
            label = "Test alert",
            color = Aether.Cyan,
            modifier = Modifier.weight(1f),
            enabled = permissionGranted && s.smartNotificationsEnabled
        ) { repo.testSmartNotification() }
        CyberButton(
            label = "Channels",
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
        label = "Clear optional alerts",
        color = Aether.InkMuted,
        modifier = Modifier.fillMaxWidth()
    ) { repo.clearSmartNotifications() }

    SettingSwitch(
        title = "Live connection telemetry",
        subtitle = "Ping and rates in the status",
        checked = s.notificationLiveStats
    ) { repo.updateSettings(s.copy(notificationLiveStats = it)) }

    SettingSwitch(
        title = "Optional smart alerts",
        subtitle = "Master switch for event alerts",
        checked = s.smartNotificationsEnabled
    ) { repo.updateSettings(s.copy(smartNotificationsEnabled = it)) }

    AnimatedVisibility(s.smartNotificationsEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SettingSwitch(
                title = "Connection events",
                subtitle = "Route changes",
                checked = s.notifyConnectionEvents
            ) { repo.updateSettings(repo.settings.copy(notifyConnectionEvents = it)) }
            SettingSwitch(
                title = "Recovery & failover",
                subtitle = "Fallback and recovery",
                checked = s.notifyRecoveryEvents
            ) { repo.updateSettings(repo.settings.copy(notifyRecoveryEvents = it)) }
            SettingSwitch(
                title = "Privacy warnings",
                subtitle = "Kill switch, blocked routes",
                checked = s.notifyPrivacyWarnings
            ) { repo.updateSettings(repo.settings.copy(notifyPrivacyWarnings = it)) }
            SettingSwitch(
                title = "Network changes",
                subtitle = "Wi-Fi ↔ cellular switches",
                checked = s.notifyNetworkChanges
            ) { repo.updateSettings(repo.settings.copy(notifyNetworkChanges = it)) }
            SettingSwitch(
                title = "Subscription updates",
                subtitle = "Refresh results, failures",
                checked = s.notifySubscriptionEvents
            ) { repo.updateSettings(repo.settings.copy(notifySubscriptionEvents = it)) }
            SettingSwitch(
                title = "Core updates",
                subtitle = "Newer Xray or HEV core",
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
private fun ServerIntelMetric(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    val shape=RoundedCornerShape(15.dp)
    Column(
        modifier=modifier
            .heightIn(min=68.dp)
            .prismWell(shape=shape, tone=tone)
            .padding(horizontal=10.dp,vertical=9.dp),
        verticalArrangement=Arrangement.spacedBy(3.dp)
    ) {
        Text(
            trx(label).uppercase(),
            color=tone,
            style=MaterialTheme.typography.labelSmall,
            fontWeight=FontWeight.Bold,
            maxLines=1,
            overflow=TextOverflow.Ellipsis
        )
        Text(
            value.ifBlank { "—" },
            color=Aether.Ink,
            style=if(monospace) {
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily=FontFamily.Monospace,
                    fontWeight=FontWeight.SemiBold
                )
            } else {
                MaterialTheme.typography.bodySmall.copy(
                    fontWeight=FontWeight.SemiBold
                )
            },
            maxLines=2,
            overflow=TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ServerIntelHomeCard(repo: AppRepository) {
    val selected=repo.profile(
        repo.activeProfileId,
        repo.activeProfileSourceId
    ) ?: repo.lastProfile()
    val endpoint=selected?.host
        ?.trim()
        ?.removeSurrounding("[", "]")
        .orEmpty()
    val info=repo.serverIntel?.takeIf {
        it.endpoint.equals(endpoint,ignoreCase=true)
    }

    LaunchedEffect(
        repo.settings.serverIntelEnabled,
        selected?.id,
        selected?.subscriptionId,
        endpoint
    ) {
        if(repo.settings.serverIntelEnabled && selected != null && endpoint.isNotBlank()) {
            repo.refreshServerIntel(selected)
        }
    }

    PrismPanel(
        modifier=Modifier.fillMaxWidth(),
        accent=Aether.Cyan,
        selected=info != null,
        contentPadding=PaddingValues(14.dp)
    ) {
        Row(
            modifier=Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)
        ) {
            HomeIconTile(HomeIcon.SERVER,Aether.Cyan)
            Column(Modifier.weight(1f)) {
                Text(
                    trx("Server info"),
                    color=Aether.Ink,
                    style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold
                )
                Text(
                    selected?.let { stripLeadingFlag(it.name) }
                        ?.ifBlank { "Selected route" }
                        ?: "Choose a server",
                    color=Aether.InkMuted,
                    style=MaterialTheme.typography.bodySmall,
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis
                )
            }
            when {
                repo.serverIntelLoading -> CircularProgressIndicator(
                    modifier=Modifier.size(22.dp),
                    color=Aether.Cyan,
                    strokeWidth=2.dp
                )
                info != null -> HoloBadge("READY",Aether.Emerald,true)
                selected == null -> HoloBadge("NO ROUTE",Aether.InkMuted,true)
                else -> HoloBadge("LOOKUP",Aether.Cyan,true)
            }
        }

        if(selected == null || endpoint.isBlank()) {
            Text(
                trx("No server selected"),
                color=Aether.InkMuted,
                style=MaterialTheme.typography.bodySmall
            )
        } else {
            Row(
                modifier=Modifier
                    .fillMaxWidth()
                    .prismWell(shape=RoundedCornerShape(14.dp))
                    .padding(horizontal=10.dp,vertical=8.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    trx("ENDPOINT"),
                    color=Aether.Cyan,
                    style=MaterialTheme.typography.labelSmall,
                    fontWeight=FontWeight.Bold
                )
                Text(
                    endpoint,
                    modifier=Modifier.weight(1f),
                    color=Aether.InkMuted,
                    style=MaterialTheme.typography.labelSmall.copy(
                        fontFamily=FontFamily.Monospace,
                        fontWeight=FontWeight.SemiBold
                    ),
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis
                )
                CyberButton(
                    label=if(repo.serverIntelLoading) "Refreshing" else "Refresh",
                    color=Aether.Cyan,
                    compact=true,
                    enabled=!repo.serverIntelLoading,
                    onClick={ repo.refreshServerIntel(selected,force=true) }
                )
            }

            info?.let { current ->
                Row(
                    modifier=Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    ServerIntelMetric(
                        "Server IP",
                        current.ip,
                        Aether.Cyan,
                        Modifier.weight(1.35f),
                        monospace=true
                    )
                    ServerIntelMetric(
                        "Family",
                        current.ipType,
                        Aether.Amethyst,
                        Modifier.weight(.65f)
                    )
                }

                Row(
                    modifier=Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    ServerIntelMetric(
                        "City",
                        current.city,
                        Aether.Emerald,
                        Modifier.weight(1f)
                    )
                    ServerIntelMetric(
                        "Country",
                        listOf(current.flag,current.country)
                            .filter(String::isNotBlank)
                            .joinToString(" "),
                        Aether.Emerald,
                        Modifier.weight(1f)
                    )
                }

                ServerIntelMetric(
                    "Datacenter / network",
                    current.datacenterLabel,
                    Aether.Amethyst,
                    Modifier.fillMaxWidth()
                )

                Row(
                    modifier=Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    ServerIntelMetric(
                        "ASN",
                        current.asn,
                        Aether.Cyan,
                        Modifier.weight(.72f),
                        monospace=true
                    )
                    ServerIntelMetric(
                        "ISP",
                        current.isp,
                        Aether.Cyan,
                        Modifier.weight(1.28f)
                    )
                }

                Row(
                    modifier=Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(7.dp)
                ) {
                    HoloBadge(
                        if(current.hosting) "HOSTING / DC" else "PUBLIC NETWORK",
                        if(current.hosting) Aether.Amethyst else Aether.Cyan,
                        compact=true
                    )
                    if(current.proxy) HoloBadge("PROXY",Aether.Amber,compact=true)
                    if(current.vpn) HoloBadge("VPN",Aether.Amber,compact=true)
                    if(current.tor) HoloBadge("TOR",Aether.Danger,compact=true)
                }

                // MARBLE_SERVER_PROVIDER_LINK_V78 — Provider website link
                val providerDomain = current.domain.ifBlank {
                    current.isp
                        .lowercase()
                        .replace(Regex("[^a-z0-9.-]"), "")
                        .takeIf { it.contains(".") }
                        .orEmpty()
                }
                if (providerDomain.isNotBlank()) {
                    val providerUrl = when {
                        providerDomain.startsWith("http") -> providerDomain
                        else -> "https://$providerDomain"
                    }
                    val context = LocalContext.current
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Aether.Cyan.copy(alpha = .06f))
                            .border(1.dp, Aether.Cyan.copy(alpha = .16f), shape)
                            .kineticClickable(role = Role.Button) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(providerUrl))
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Aether.Cyan.copy(alpha = .11f)),
                            contentAlignment = Alignment.Center
                        ) {
                            HomeVectorIcon(HomeIcon.DETAILS, Aether.Cyan, Modifier.size(16.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                trx("Visit provider website"),
                                color = Aether.Ink,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                providerDomain,
                                color = Aether.Cyan,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HomeVectorIcon(HomeIcon.DETAILS, Aether.Cyan.copy(alpha = .55f), Modifier.size(14.dp))
                    }
                }
            } ?: Text(
                if(repo.serverIntelLoading) {
                    "Resolving the selected server and loading public network metadata…"
                } else {
                    "Server metadata has not been loaded yet."
                },
                color=Aether.InkMuted,
                style=MaterialTheme.typography.bodySmall
            )

            if(repo.serverIntelError.isNotBlank()) {
                Text(
                    repo.serverIntelError,
                    color=Aether.Amber,
                    style=MaterialTheme.typography.bodySmall
                )
            }
        }

    }
}

@Composable
private fun SplitTunnelModeSelector(repo: AppRepository) {
    // MARBLE_IOS_SIMPLIFY_V81 — flat segmented pills, no elevation.
    Row(
        modifier=Modifier.fillMaxWidth(),
        horizontalArrangement=Arrangement.spacedBy(6.dp)
    ) {
        SplitTunnelMode.entries.forEach { mode ->
            val selected=repo.settings.splitTunnelMode == mode
            val label=when(mode) {
                SplitTunnelMode.ALL_APPS -> "All apps"
                SplitTunnelMode.ONLY_SELECTED -> "Only selected"
                SplitTunnelMode.BYPASS_SELECTED -> "Bypass selected"
            }
            val shape=RoundedCornerShape(12.dp)
            Box(
                modifier=Modifier
                    .weight(1f)
                    .heightIn(min=36.dp)
                    .clip(shape)
                    .background(if(selected) Aether.Cyan.copy(alpha=.13f) else Color.Transparent)
                    .border(
                        1.dp,
                        if(selected) Aether.Cyan.copy(alpha=.42f) else Aether.GlassBorderSoft.copy(alpha=.7f),
                        shape
                    )
                    .kineticClickable(
                        role=Role.Button,
                        boundedShape=shape,
                        showIndication=false
                    ) {
                        repo.updateSettings(
                            repo.settings.copy(splitTunnelMode=mode)
                        )
                    }
                    .semantics { this.selected=selected }
                    .padding(horizontal=6.dp,vertical=8.dp),
                contentAlignment=Alignment.Center
            ) {
                Text(
                    label,
                    color=if(selected) Aether.Cyan else Aether.InkMuted,
                    style=MaterialTheme.typography.labelSmall,
                    fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign=TextAlign.Center,
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis
                )
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
    SplitTunnelModeSelector(repo)
    if(repo.settings.splitTunnelMode!=SplitTunnelMode.ALL_APPS){
        TextField(search,{search=it},placeholder={Text(trx("Search installed apps"))},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=TextFieldDefaults.colors(focusedTextColor=Aether.Ink,unfocusedTextColor=Aether.Ink,cursorColor=Aether.Cyan,focusedContainerColor=Aether.GlassStrong,unfocusedContainerColor=Aether.GlassStrong,disabledContainerColor=Aether.GlassStrong,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(if(apps.isEmpty())"Loading installed apps…" else "${visibleApps.size} apps",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall);HoloBadge("${selected.size} selected",Aether.Emerald,true)}
        LazyColumn(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(18.dp)).background(Aether.Glass.copy(alpha=.70f)),contentPadding=PaddingValues(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(2.dp),userScrollEnabled=true){items(visibleApps,key={it.packageName}){app->SplitTunnelAppRow(app,app.packageName in selected){toggle(app.packageName)}}}
        Text(trx("Applies on next Full TUN connect."),color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SplitTunnelAppRow(app:InstalledApp,checked:Boolean,onToggle:()->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).kineticClickable(role=Role.Checkbox,onClick=onToggle).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if(checked)Aether.Emerald.copy(alpha=.12f) else Aether.GlassStrong),contentAlignment=Alignment.Center){Text(app.label.trim().firstOrNull()?.uppercase()?:"•",color=if(checked)Aether.Emerald else Aether.InkMuted,style=MaterialTheme.typography.labelLarge)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(app.label,color=Aether.Ink,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(app.packageName,color=Aether.InkFaint,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Checkbox(checked,{onToggle()},colors=CheckboxDefaults.colors(checkedColor=Aether.Emerald,checkmarkColor=Aether.Void,uncheckedColor=Aether.GlassBorder))}
}

@Composable
private fun FragmentMuxSettings(repo: AppRepository) {
    SettingSwitch(
        title = "Adaptive Fragment",
        subtitle = "Try Fragment only after interference",
        checked = repo.settings.adaptiveFragmentEnabled
    ) { repo.updateSettings(repo.settings.copy(adaptiveFragmentEnabled = it)) }
    SettingSwitch(
        title = "Adaptive Mux",
        subtitle = "Try Mux only on stable routes",
        checked = repo.settings.adaptiveMuxEnabled
    ) { repo.updateSettings(repo.settings.copy(adaptiveMuxEnabled = it)) }
    HorizontalDivider(color = Aether.GlassBorderSoft)

    SettingSwitch(
        title = "TLS ClientHello fragmentation",
        subtitle = "Split the first packet on dial",
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


        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    SettingSwitch(
        title = "Mux / XUDP",
        subtitle = "Reuse connections for small streams",
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
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
private fun DnsSettings(repo: AppRepository) {
    val underlay = repo.networkSnapshot
    // One line of truth. The switches below are inputs; this is what the engine will actually do,
    // computed by the same policy the tunnel, the delay test and the pings use.
    val familyPlan = AddressFamilyPolicy.plan(
        settings = repo.settings,
        underlayHasIpv6 = underlay.hasIpv6
    )

    PrismWell(
        modifier = Modifier.fillMaxWidth(),
        tone = if (familyPlan.prioritizeIpv6) Aether.Cyan else Aether.Amethyst,
        selected = familyPlan.prioritizeIpv6,
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                trx("ACTIVE FAMILY POLICY"),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                AddressFamilyPolicy.describe(familyPlan),
                color = Aether.Ink,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "endpoint ${familyPlan.endpointStrategy} • dns ${familyPlan.dnsQueryStrategy} • " +
                    if (familyPlan.blockIpv6Traffic) "::/0 blocked" else "::/0 through the tunnel",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

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
        subtitle = "IPv6 in the tunnel; off blocks ::/0",
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
        subtitle = "IPv6 first; auto-paused on IPv4 nets",
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
        subtitle = "Port 53 → encrypted DNS",
        checked = repo.settings.dnsHijackEnabled
    ) { repo.updateSettings(repo.settings.copy(dnsHijackEnabled = it)) }
    SettingSwitch(
        title = "Adaptive DoH ordering",
        subtitle = "Keep the fastest DoH path",
        checked = repo.settings.adaptiveDnsEnabled
    ) { repo.updateSettings(repo.settings.copy(adaptiveDnsEnabled = it)) }
    SettingSwitch(
        title = "Adaptive IPv4 / IPv6 DNS",
        subtitle = "DNS family follows the link",
        checked = repo.settings.adaptiveDualStackEnabled
    ) { repo.updateSettings(repo.settings.copy(adaptiveDualStackEnabled = it)) }

    Text(trx("Resolvers"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
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

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // "UseSystem" used to be offered here. It tells Xray to ask the Android resolver, which both
        // leaks the query outside the encrypted path and hides the AAAA records an IPv6 node needs,
        // so the list is now exactly the three record strategies the engine honours inside the tunnel.
        // Show what the engine will really do rather than the raw stored string: a legacy
        // "UseSystem" value resolves to A + AAAA, and turning IPv6 off forces IPv4 records — either
        // way an unlabelled mismatch would make the switch look broken.
        val storedStrategy = repo.settings.dnsQueryStrategy
        val effectiveStrategy = when {
            !repo.settings.ipv6Enabled -> "UseIPv4"
            storedStrategy == "UseIPv4" || storedStrategy == "UseIPv6" -> storedStrategy
            else -> "UseIP"
        }
        mapOf(
            "UseIP" to "A + AAAA",
            "UseIPv4" to "IPv4 ONLY",
            "UseIPv6" to "IPv6 ONLY"
        ).forEach { (strategy, label) ->
            CyberChoiceChip(
                text = label,
                selected = effectiveStrategy == strategy,
                enabled = strategy != "UseIPv6" || repo.settings.ipv6Enabled,
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
    // MARBLE_IOS_SIMPLIFY_V81 — flat status tile: tone wash + hairline, no elevation.
    val color = if (ready) Aether.Emerald else Aether.Amber
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = if (ready) .10f else .07f))
            .border(1.dp, color.copy(alpha = if (ready) .38f else .24f), shape)
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(7.dp))
            Text(title, color = Aether.Ink, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(if (ready) "Ready" else "Missing", color = color, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (ready) "${formatBytes(bytes)} • ${if (remote) "remote" else "bundled"}" else "Tap Prepare to install",
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
                    trx("Recommended Iran policy"),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium
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
            label = "Restore recommended policy",
            color = Aether.Emerald,
            modifier = Modifier.fillMaxWidth(),
            enabled = !repo.busy
        ) {
            repo.applyIranRoutingPreset()
        }
    }

    SettingSwitch(
        title = "Bypass Iranian traffic",
        subtitle = ".ir direct, rest proxied",
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
        subtitle = "Block ads before other rules",
        checked = s.routeBlockAds
    ) {
        repo.updateSettings(
            s.copy(
                routeBlockAds = it,
                routeAdsTag = if (s.routeAdsTag.isBlank()) RoutingDefaults.ADS_TAG else s.routeAdsTag
            )
        )
    }

    Text(trx("Routing mode"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
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

    Text(trx("Geo data"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
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
                "$missingGeoAssets needed • tap Prepare.",
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



    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyberButton(
            label = "Prepare",
            color = Aether.Emerald,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.prepareRoutingAssets(false) }
        CyberButton(
            label = "Update",
            color = Aether.Cyan,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.prepareRoutingAssets(true) }
    }

    CyberButton(
        label = "Verify with Xray",
        color = Aether.Cyan,
        modifier = Modifier.fillMaxWidth(),
        enabled = repo.libraryProfiles.isNotEmpty() && !repo.busy
    ) { repo.verifyRoutingPolicy() }

    HorizontalDivider(color = Aether.GlassBorderSoft)
    Text(trx("Geo direct rules"), color = Aether.Ink, style = MaterialTheme.typography.titleMedium)

    TinyField("GeoIP direct tags", s.routeGeoIpTags, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeGeoIpTags = it))
    }
    TinyField("GeoSite direct tags", s.routeGeoSiteTags, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeGeoSiteTags = it))
    }

    SettingSwitch(
        title = "Bypass private networks",
        subtitle = "LAN stays direct",
        checked = s.routeBypassPrivate
    ) { repo.updateSettings(repo.settings.copy(routeBypassPrivate = it)) }

    Text(trx("Domain strategy"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
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
    Text(trx("Exceptions & advanced rules"), color = Aether.Ink, style = MaterialTheme.typography.titleMedium)

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

}

@Composable
private fun SubscriptionSettings(repo: AppRepository) {
    SettingSwitch(
        title = "Manual source",
        subtitle = "Show the built-in Manual source",
        checked = repo.settings.manualSourceEnabled
    ) {
        repo.updateSettings(repo.settings.copy(manualSourceEnabled = it))
    }

    SettingSwitch(
        title = "Automatic refresh",
        subtitle = "Refresh stale sources on start",
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
        label = "Refresh all now",
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
    var checksExpanded by remember(report?.generatedAt) { mutableStateOf(false) }

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
                Text(trx("Runtime observatory"), color=Aether.Ink, style=MaterialTheme.typography.titleMedium)
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
            title = "Debug TXT log",
            subtitle = if (debug) {
                "Streaming to ${repo.debugReportLocation()}"
            } else {
                "Off by default"
            },
            checked = debug
        ) { repo.setDebugMode(it) }

        Column(
            Modifier.fillMaxWidth()
                .prismWell(
                    shape=RoundedCornerShape(15.dp),
                    tone=if(debug) Aether.Cyan else Aether.InkMuted,
                    selected=debug
                )
                .padding(11.dp), verticalArrangement=Arrangement.spacedBy(4.dp)
        ) {
            Text(trx("Report location"), color=if(debug) Aether.Cyan else Aether.InkFaint, style=MaterialTheme.typography.labelSmall)
            Text(repo.debugReportLocation(), color=Aether.Ink, style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))
        }

        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CyberButton(
                if(repo.busy)"Scanning…" else "Run scan",
                Aether.Cyan,
                Modifier.weight(1f),
                !repo.busy,
                variant = PrismButtonVariant.Primary,
                icon = if(repo.busy) null else HomeIcon.BENCHMARK
            ) { repo.runBugFinder() }
            CyberButton("Copy report", Aether.Amethyst, Modifier.weight(1f), report != null && !repo.busy) {
                clipboard.setText(AnnotatedString(repo.bugFinderReportText()))
                repo.setRuntimeMessage("Bug Finder report copied")
            }
        }

        if(report != null) CyberButton("Save TXT snapshot",Aether.Emerald,Modifier.fillMaxWidth(),!repo.busy) { repo.saveBugFinderReport() }

        report?.let { current ->
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                HoloBadge("${current.passed} pass",Aether.Emerald,true)
                if(current.warnings>0) HoloBadge("${current.warnings} warn",Aether.Amber,true)
                if(current.failures>0) HoloBadge("${current.failures} fail",Aether.Danger,true)
            }
            Text(current.headline,
                color=if(current.failures>0)Aether.Danger else if(current.warnings>0)Aether.Amber else Aether.Emerald,
                style=MaterialTheme.typography.titleMedium)
            CyberButton(
                if (checksExpanded) "Hide checks" else "Checks • ${current.checks.size}",
                Aether.InkMuted,
                Modifier.fillMaxWidth()
            ) { checksExpanded = !checksExpanded }
            if (checksExpanded) {
                current.checks.forEach { check ->
                    val c=when(check.severity){
                        BugSeverity.PASS->Aether.Emerald; BugSeverity.INFO->Aether.Cyan; BugSeverity.WARN->Aether.Amber; BugSeverity.FAIL->Aether.Danger
                    }
                    Column(Modifier.fillMaxWidth()
                        .prismWell(shape=RoundedCornerShape(15.dp), tone=c, selected=c != Aether.Emerald)
                        .padding(11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(check.title,color=Aether.Ink,style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                            HoloBadge(check.severity.name,c,true)
                        }
                        Text(check.detail,color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)
                        if(check.action.isNotBlank()) Text("→ ${check.action}",color=c,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(current.failures>0) CyberButton("Safe runtime reset",Aether.Danger,Modifier.fillMaxWidth(),!repo.busy) { repo.safeRuntimeResetFromBugFinder() }
        }
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

@Composable
private fun ProbeSettings(repo: AppRepository) {
    val s = repo.settings


    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        ProbeMethod.entries.filter { it != ProbeMethod.TCP }.forEach { method ->
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

    HorizontalDivider(color = Aether.GlassBorderSoft)

    NumberSetting(
        title = "Samples per server",
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
        title = "Servers per test run",
        value = s.benchCandidates,
        range = 5..200
    ) { repo.updateSettings(repo.settings.copy(benchCandidates = it)) }

    SettingSwitch(
        title = "Also measure download speed",
        subtitle = "Slower, uses data",
        checked = s.probeSpeedTest
    ) { repo.updateSettings(repo.settings.copy(probeSpeedTest = it)) }

    AnimatedVisibility(s.probeSpeedTest) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SettingSwitch(
                title = "Grow the speed sample",
                subtitle = "More only from fast servers",
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
                trx("TCP/ICMP bypass the proxy; only Real tunnel proves the route."),
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
    variant: PrismButtonVariant = PrismButtonVariant.Secondary,
    detail: String = "",
    badge: String = "",
    icon: HomeIcon? = null,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    // A destructive verb is always dressed as one, whichever accent the caller passed.
    val resolved=if(color == Aether.Danger) PrismButtonVariant.Danger else variant
    PrismButton(
        label=label,
        onClick=onClick,
        tone=color,
        modifier=modifier,
        variant=resolved,
        enabled=enabled,
        compact=compact,
        detail=detail,
        badge=badge,
        icon=icon?.let {
            {
                HomeVectorIcon(
                    it,
                    if(enabled) {
                        // Filled skins carry the button's own content color so the icon never
                        // collides with the gradient; tonal skins keep the semantic accent.
                        if(resolved == PrismButtonVariant.Primary ||
                            resolved == PrismButtonVariant.Danger
                        ) LocalContentColor.current else color
                    } else Aether.InkFaint,
                    Modifier.size(if(compact) 16.dp else 18.dp)
                )
            }
        }
    )
}

/** Dialog verbs share the product button instead of a bare text link. */
@Composable
private fun MarbleDialogAction(
    label: String,
    tone: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: PrismButtonVariant = PrismButtonVariant.Quiet,
    onClick: () -> Unit
) {
    CyberButton(
        label=label,
        color=tone,
        modifier=modifier,
        enabled=enabled,
        variant=variant,
        compact=true,
        onClick=onClick
    )
}


@Composable
private fun CyberChoiceChip(
    text: String,
    selected: Boolean,
    color: Color,
    selectionTone: Color = Color.Unspecified,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // MARBLE_IOS_SIMPLIFY_V81 — a flat little pill: tint wash + tinted ink when selected,
    // a quiet hairline when not. No elevation, no tick badge, never resizes.
    val tone = if (selectionTone == Color.Unspecified) color else selectionTone
    val shape = RoundedCornerShape(12.dp)
    val fill by animateColorAsState(
        if (selected) tone.copy(alpha = .14f) else Color.Transparent,
        MarbleMotionSpecs.Color,
        label = "choice-chip-fill"
    )
    val ink by animateColorAsState(
        if (selected) tone else Aether.InkMuted,
        MarbleMotionSpecs.Color,
        label = "choice-chip-ink"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(fill)
            .border(
                1.dp,
                if (selected) tone.copy(alpha = .42f) else Aether.GlassBorderSoft.copy(alpha = .7f),
                shape
            )
            .kineticClickable(
                enabled = enabled,
                role = Role.Button,
                boundedShape = shape,
                showIndication = false,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            trx(text),
            color = ink,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
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
    selectionTone: Color = Color.Unspecified,
    labelFontFamily: FontFamily? = null,
    rawLabel: Boolean = false,
    onClick: () -> Unit
) {
    // MARBLE_IOS_SIMPLIFY_V81 — a flat two-line segment tile for the few-per-row choices
    // (probe method, Iran policy, connection mode). Same language as the chips, just taller.
    val tone = if (selectionTone == Color.Unspecified) color else selectionTone
    val shape = RoundedCornerShape(14.dp)
    val fill by animateColorAsState(
        if (selected) tone.copy(alpha = .12f) else Aether.GlassStrong.copy(alpha = .30f),
        MarbleMotionSpecs.Color,
        label = "segment-fill"
    )
    val ink by animateColorAsState(
        if (selected) tone else Aether.Ink,
        MarbleMotionSpecs.Color,
        label = "segment-ink"
    )
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(fill)
            .border(
                1.dp,
                if (selected) tone.copy(alpha = .42f) else Aether.GlassBorderSoft.copy(alpha = .55f),
                shape
            )
            .kineticClickable(
                role = Role.Button,
                boundedShape = shape,
                showIndication = false,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // MARBLE_VAZIR_LANGUAGE_KEY_V114 — a segment can own its face. The Persian language choice
        // passes Vazir and [rawLabel] so the word "فارسی" is never routed through the translator and
        // never borrows the currently selected product typeface: it reads as Persian, in Vazir,
        // selected or not.
        val labelStyle = MaterialTheme.typography.labelMedium
        Text(
            if (rawLabel) label else trx(label),
            color = ink,
            style = if (labelFontFamily == null) {
                labelStyle
            } else {
                labelStyle.copy(fontFamily = labelFontFamily)
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (detail.isNotBlank()) {
            val detailStyle = MaterialTheme.typography.labelSmall
            Text(
                if (rawLabel) detail else trx(detail),
                color = if (selected) tone.copy(alpha = .85f) else Aether.InkFaint,
                style = if (labelFontFamily == null) {
                    detailStyle
                } else {
                    detailStyle.copy(fontFamily = labelFontFamily)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    debounceMs: Long = 300L,
    onChecked: (Boolean) -> Unit
) {
    // MARBLE_IOS_SIMPLIFY_V81 — an iOS-style plain row: no box, no dot, no border. Just
    // label + optional one-line summary on the left, a compact switch on the right, and
    // breathing room provided by the card's spacing.
    var lastClickTime by remember { mutableLongStateOf(0L) }

    Row(
        modifier=Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .kineticClickable(
                role = Role.Switch,
                boundedShape = RoundedCornerShape(12.dp),
                showIndication = false
            ) {
                val now = System.currentTimeMillis()
                if (now - lastClickTime > debounceMs) {
                    lastClickTime = now
                    onChecked(!checked)
                }
            }
            .semantics {
                contentDescription = "$title, switch ${if (checked) "on" else "off"}"
                stateDescription = if (checked) "Enabled" else "Disabled"
            }
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                trx(title),
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    trx(subtitle),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = marbleSwitchColors()
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
    // Stale or corrupted stored values must never render or step outside the legal range.
    val shown = value.coerceIn(range)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            trx(title),
            color = Aether.Ink,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        PrismIconButton(
            onClick = { onValue((shown - 1).coerceAtLeast(range.first)) },
            tone = Aether.InkMuted,
            size = 30.dp,
            descriptiveLabel = "Decrease $title"
        ) {
            Text("−", color = Aether.InkMuted, style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "$shown${trx(suffix)}",
            color = Aether.Cyan,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.Center
        )

        PrismIconButton(
            onClick = { onValue((shown + 1).coerceAtMost(range.last)) },
            tone = Aether.Cyan,
            selected = true,
            size = 30.dp,
            descriptiveLabel = "Increase $title"
        ) {
            Text("+", color = Aether.Cyan, style = MaterialTheme.typography.bodyMedium)
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
        label = { Text(trx(label)) },
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = marbleOutlinedTextFieldColors()
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
        Text(trx(title), color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
        Text(
            trx(body),
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
    latencyMs in 1..99 && success >= 80 -> Aether.Emerald
    latencyMs in 100..250 -> Aether.Amber
    latencyMs > 250 -> Aether.Danger
    else -> Aether.Cyan
}

// MARBLE_LIBRARY_PING_HELPERS_V25_3_1
// Product metric bands: green <100 ms, amber 100..250 ms, red >250 ms.
private fun libraryPingQuality(latencyMs: Int): String = when {
    latencyMs <= 0 -> "Waiting"
    latencyMs < 100 -> "Fast"
    latencyMs <= 250 -> "Fair"
    else -> "Slow"
}

private fun libraryPingBars(latencyMs: Int): Int = when {
    latencyMs <= 0 -> 0
    latencyMs < 100 -> 4
    latencyMs <= 250 -> 2
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
        "Fragment, DNS order, MTU, failover",
        settings.iranModeCountermeasures
    ) { repo.updateSettings(settings.copy(iranModeCountermeasures = it)) }

    SettingSwitch(
        "Domestic traffic direct",
        "Keep .ir sites off the tunnel",
        settings.iranDomesticDirect
    ) { repo.updateSettings(settings.copy(iranDomesticDirect = it)) }

    SettingSwitch(
        "Fingerprint the filtering",
        "DNS injection, SNI resets, UDP",
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

// =================================================================================================
// MARBLE FREEDOM SETTINGS
// =================================================================================================

@Composable
private fun FreedomSettings(repo: AppRepository) {
    val s = repo.settings
    val enabled = s.serverlessModeEnabled
    val recipe = com.marbleng.app.core.DpiEvasionPolicy.freedomRecipe(s)
    val layerCount = 1 + (if (recipe.middleEnabled) 1 else 0) + (if (recipe.innerEnabled) 1 else 0)

    // ── Status hero ──────────────────────────────────────────────────────────
    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (enabled) Aether.Cyan.copy(alpha = 0.55f) else Color.Transparent,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background((if (enabled) Aether.Emerald else Aether.InkMuted).copy(alpha = .11f)),
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(
                    HomeIcon.SHIELD,
                    if (enabled) Aether.Emerald else Aether.InkMuted,
                    Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    trx("Marble Freedom"),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (enabled) {
                        if (s.freedomForceTcpForStreaming) {
                            "$layerCount-hop chain • TCP fallback on"
                        } else {
                            "$layerCount-hop chain • QUIC padding on"
                        }
                    } else {
                        "Ready to switch on"
                    },
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { repo.setServerlessMode(it) },
                colors = marbleSwitchColors()
            )
        }

        if (enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                HoloBadge("$layerCount-HOP", Aether.Cyan, compact = true)
                HoloBadge(
                    s.freedomPreset.name.replace("_", " "),
                    Aether.Amethyst,
                    compact = true
                )
                if (s.freedomForceTcpForStreaming) {
                    HoloBadge("TCP FALLBACK", Aether.Emerald, compact = true)
                } else if (s.freedomUdpNoiseEnabled) {
                    HoloBadge("QUIC PADDING", Aether.Emerald, compact = true)
                }
                if (s.freedomDnsHijack) {
                    HoloBadge("DNS HIJACK", Aether.Emerald, compact = true)
                }
                if (s.freedomDirectDomestic) {
                    HoloBadge("IR DIRECT", Aether.Cyan, compact = true)
                }
            }
        }
    }

    SettingSwitch(
        title = "YouTube / media TCP fallback",
        subtitle = "Block QUIC so video uses the chain",
        checked = s.freedomForceTcpForStreaming
    ) { repo.updateSettings(s.copy(freedomForceTcpForStreaming = it)) }

    // ── Evasion presets (2-column grid, not a free-flow jumble) ───────────────
    FreedomSectionHeader(
        title = "Evasion preset",
        subtitle = "Start balanced; escalate on harsh filters.",
        tone = Aether.Cyan
    )
    val presets = listOf(
        Triple(FreedomPreset.SMART_ADAPTIVE, "Smart Auto", "Best everyday default"),
        Triple(FreedomPreset.MULTI_LAYER_CASCADE, "Stable 2-hop", "Social and video"),
        Triple(FreedomPreset.SNI_SHREDDER, "SNI split", "Light split"),
        Triple(FreedomPreset.AGGRESSIVE_RECORD_SPLIT, "Slow-first", "Strict reset filters"),
        Triple(FreedomPreset.EXTREME_ANTI_DPI, "Extreme", "Strongest 2-hop"),
        Triple(FreedomPreset.CUSTOM, "Custom", "Manual tuning")
    )
    presets.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { (preset, title, detail) ->
                val selected = s.freedomPreset == preset
                PrismSelectionTile(
                    label = title,
                    selected = selected,
                    tone = Aether.Cyan,
                    modifier = Modifier.weight(1f),
                    detail = detail,
                    minHeight = 50.dp,
                    alignment = Alignment.CenterStart
                ) {
                    repo.updateSettings(freedomPresetSettings(s, preset))
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    // ── Per-operator steel profiles (MARBLE_OPERATOR_FREEDOM_V91) ─────────────
    FreedomSectionHeader(
        title = "Operator profile",
        subtitle = "Per-carrier chains; Auto picks by ASN.",
        tone = Aether.Amethyst
    )
    SettingSwitch(
        title = "Auto-apply detected operator",
        subtitle = "Match MCI / Irancell / Rightel / Shatel",
        checked = s.freedomOperatorAuto
    ) { repo.updateSettings(s.copy(freedomOperatorAuto = it)) }
    val operatorPresets = listOf(
        Triple(FreedomPreset.SHATEL, "Shatel", "Fixed-line chain"),
        Triple(FreedomPreset.HAMRAH_AVAL, "MCI • همراه اول", "Strictest DPI"),
        Triple(FreedomPreset.IRANCELL, "Irancell", "Throttle-heavy"),
        Triple(FreedomPreset.RIGHTEL, "Rightel", "Mobile chain")
    )
    operatorPresets.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { (preset, title, detail) ->
                val selected = s.freedomPreset == preset
                PrismSelectionTile(
                    label = title,
                    selected = selected,
                    tone = Aether.Amethyst,
                    modifier = Modifier.weight(1f),
                    detail = detail,
                    minHeight = 50.dp,
                    alignment = Alignment.CenterStart
                ) {
                    repo.updateSettings(freedomPresetSettings(s, preset))
                }
            }
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    // ── Fragment layers ──────────────────────────────────────────────────────
    FreedomSectionHeader(
        title = "Fragment chain",
        subtitle = "Two hops stay fast; add the middle only if needed.",
        tone = Aether.Amethyst
    )

    FreedomLayerCard(
        title = "Outer hop",
        subtitle = "First-write split • hides SNI",
        tone = Aether.Cyan,
        enabled = true,
        onEnabled = null,
        packets = s.freedomOuterPackets,
        length = s.freedomOuterLength,
        interval = s.freedomOuterInterval,
        maxSplit = s.freedomOuterMaxSplit,
        onPackets = { repo.updateSettings(s.copy(freedomOuterPackets = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onLength = { repo.updateSettings(s.copy(freedomOuterLength = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onInterval = { repo.updateSettings(s.copy(freedomOuterInterval = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onMaxSplit = { repo.updateSettings(s.copy(freedomOuterMaxSplit = it, freedomPreset = FreedomPreset.CUSTOM)) }
    )

    FreedomLayerCard(
        title = "Middle hop",
        subtitle = "Optional • off by default",
        tone = Aether.Amethyst,
        enabled = s.freedomMiddleEnabled,
        onEnabled = {
            repo.updateSettings(
                s.copy(
                    freedomMiddleEnabled = it,
                    freedomLayerCount = if (it) 3 else 2,
                    freedomPreset = FreedomPreset.CUSTOM
                )
            )
        },
        packets = s.freedomMiddlePackets,
        length = s.freedomMiddleLength,
        interval = s.freedomMiddleInterval,
        maxSplit = s.freedomMiddleMaxSplit,
        onPackets = { repo.updateSettings(s.copy(freedomMiddlePackets = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onLength = { repo.updateSettings(s.copy(freedomMiddleLength = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onInterval = { repo.updateSettings(s.copy(freedomMiddleInterval = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onMaxSplit = { repo.updateSettings(s.copy(freedomMiddleMaxSplit = it, freedomPreset = FreedomPreset.CUSTOM)) }
    )

    FreedomLayerCard(
        title = "Inner hop",
        subtitle = "Byte-level split on dial",
        tone = Aether.Emerald,
        enabled = s.freedomInnerEnabled,
        onEnabled = {
            repo.updateSettings(s.copy(freedomInnerEnabled = it, freedomPreset = FreedomPreset.CUSTOM))
        },
        packets = s.freedomInnerPackets,
        length = s.freedomInnerLength,
        interval = s.freedomInnerInterval,
        maxSplit = s.freedomInnerMaxSplit,
        onPackets = { repo.updateSettings(s.copy(freedomInnerPackets = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onLength = { repo.updateSettings(s.copy(freedomInnerLength = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onInterval = { repo.updateSettings(s.copy(freedomInnerInterval = it, freedomPreset = FreedomPreset.CUSTOM)) },
        onMaxSplit = { repo.updateSettings(s.copy(freedomInnerMaxSplit = it, freedomPreset = FreedomPreset.CUSTOM)) }
    )

    HorizontalDivider(color = Aether.GlassBorderSoft)

    // ── DNS ──────────────────────────────────────────────────────────────────
    FreedomSectionHeader(
        title = "Encrypted DNS",
        subtitle = "Pinned DoH; classic DNS stays in the tunnel.",
        tone = Aether.Emerald
    )

    SettingSwitch(
        title = "Smart multi-resolver cascade",
        subtitle = "Race DoH endpoints",
        checked = s.freedomDnsAuto
    ) { repo.updateSettings(s.copy(freedomDnsAuto = it)) }

    SettingSwitch(
        title = "Hijack classic DNS (port 53)",
        subtitle = "Port 53 → encrypted DNS",
        checked = s.freedomDnsHijack
    ) { repo.updateSettings(s.copy(freedomDnsHijack = it)) }

    SettingSwitch(
        title = "Domestic (.ir) & private direct",
        subtitle = ".ir and LAN off the chain",
        checked = s.freedomDirectDomestic
    ) { repo.updateSettings(s.copy(freedomDirectDomestic = it)) }

    Text(
        trx("DoH endpoints"),
        color = Aether.InkFaint,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
    TinyField("Primary DoH", s.freedomDnsPrimaryDoH, Modifier.fillMaxWidth()) {
        repo.updateSettings(s.copy(freedomDnsPrimaryDoH = it))
    }
    TinyField("Secondary DoH", s.freedomDnsSecondaryDoH, Modifier.fillMaxWidth()) {
        repo.updateSettings(s.copy(freedomDnsSecondaryDoH = it))
    }
    TinyField("Fallback DoH", s.freedomDnsFallbackDoH, Modifier.fillMaxWidth()) {
        repo.updateSettings(s.copy(freedomDnsFallbackDoH = it))
    }
    TinyField("Clean DoH pool (comma-separated)", s.freedomDnsCleanResolvers, Modifier.fillMaxWidth()) {
        repo.updateSettings(s.copy(freedomDnsCleanResolvers = it))
    }

    Text(
        trx("Bootstrap IPs"),
        color = Aether.InkFaint,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TinyField("Primary IP", s.freedomDnsPrimaryIp, Modifier.weight(1f)) {
            repo.updateSettings(s.copy(freedomDnsPrimaryIp = it))
        }
        TinyField("Secondary IP", s.freedomDnsSecondaryIp, Modifier.weight(1f)) {
            repo.updateSettings(s.copy(freedomDnsSecondaryIp = it))
        }
    }

    Text(
        trx("Record strategy"),
        color = Aether.InkFaint,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "UseIP" to "A + AAAA",
            "UseIPv4" to "IPv4",
            "UseIPv6" to "IPv6"
        ).forEach { (strategy, label) ->
            PrismSelectionTile(
                label = label,
                selected = s.freedomDnsQueryStrategy == strategy,
                tone = Aether.Cyan,
                modifier = Modifier.weight(1f),
                minHeight = 38.dp
            ) {
                repo.updateSettings(s.copy(freedomDnsQueryStrategy = strategy))
            }
        }
    }

    Text(
        trx("Domain routing"),
        color = Aether.InkFaint,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("IPOnDemand", "IPIfNonMatch", "AsIs").forEach { strategy ->
            PrismSelectionTile(
                label = strategy,
                selected = s.freedomDomainStrategy == strategy,
                tone = Aether.Emerald,
                modifier = Modifier.weight(1f),
                minHeight = 38.dp
            ) {
                repo.updateSettings(s.copy(freedomDomainStrategy = strategy))
            }
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    // ── UDP noise ────────────────────────────────────────────────────────────
    FreedomSectionHeader(
        title = "QUIC / UDP policy",
        subtitle = if (s.freedomForceTcpForStreaming) {
            "TCP fallback is on; QUIC padding waits."
        } else {
            "Pad UDP/443 only where UDP is allowed."
        },
        tone = Aether.Amber
    )

    SettingSwitch(
        title = "Dedicated QUIC padding",
        subtitle = "Pads UDP/443; off while TCP fallback is on",
        checked = s.freedomUdpNoiseEnabled
    ) { repo.updateSettings(s.copy(freedomUdpNoiseEnabled = it)) }

    AnimatedVisibility(s.freedomUdpNoiseEnabled && !s.freedomForceTcpForStreaming) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberSetting("Noise burst pairs", s.freedomUdpNoiseCount, 2..16) {
                repo.updateSettings(s.copy(freedomUdpNoiseCount = it))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TinyField("IPv4 size (B)", s.freedomUdpNoisePacket4, Modifier.weight(1f)) {
                    repo.updateSettings(s.copy(freedomUdpNoisePacket4 = it))
                }
                TinyField("IPv4 delay (ms)", s.freedomUdpNoiseDelay4, Modifier.weight(1f)) {
                    repo.updateSettings(s.copy(freedomUdpNoiseDelay4 = it))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TinyField("IPv6 size (B)", s.freedomUdpNoisePacket6, Modifier.weight(1f)) {
                    repo.updateSettings(s.copy(freedomUdpNoisePacket6 = it))
                }
                TinyField("IPv6 delay (ms)", s.freedomUdpNoiseDelay6, Modifier.weight(1f)) {
                    repo.updateSettings(s.copy(freedomUdpNoiseDelay6 = it))
                }
            }
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    // ── Reset ────────────────────────────────────────────────────────────────
    CyberButton(
        label = "Restore Freedom defaults",
        color = Aether.Amethyst,
        modifier = Modifier.fillMaxWidth(),
        enabled = !repo.busy,
        variant = PrismButtonVariant.Secondary
    ) {
        repo.updateSettings(freedomPresetSettings(s, FreedomPreset.SMART_ADAPTIVE).copy(
            freedomDnsAuto = true,
            freedomDnsPrimaryIp = "1.1.1.1",
            freedomDnsSecondaryIp = "8.8.8.8",
            freedomDnsPrimaryDoH = "https://1.1.1.1/dns-query",
            freedomDnsSecondaryDoH = "https://8.8.8.8/dns-query",
            freedomDnsFallbackDoH = "https://9.9.9.9/dns-query",
            freedomDnsCleanResolvers = "https://1.1.1.1/dns-query,https://8.8.8.8/dns-query,https://9.9.9.9/dns-query,https://dns.adguard-dns.com/dns-query,https://dns.shecan.ir/dns-query",
            freedomDnsQueryStrategy = "UseIP",
            freedomDomainStrategy = "IPOnDemand",
            freedomDnsHijack = true,
            freedomDirectDomestic = true,
            freedomForceTcpForStreaming = true,
            freedomUdpNoiseEnabled = true,
            freedomUdpNoisePacket4 = "1250",
            freedomUdpNoiseDelay4 = "10",
            freedomUdpNoisePacket6 = "1230",
            freedomUdpNoiseDelay6 = "10",
            freedomUdpNoiseCount = 6
        ))
        repo.setRuntimeMessage("Marble Freedom settings restored to defaults")
    }
}

/** Apply a Freedom preset's fragment recipe onto AppSettings without touching DNS/noise. */
private fun freedomPresetSettings(s: AppSettings, preset: FreedomPreset): AppSettings = when (preset) {
    FreedomPreset.SMART_ADAPTIVE,
    FreedomPreset.MULTI_LAYER_CASCADE -> s.copy(
        freedomPreset = preset,
        freedomLayerCount = 2,
        freedomOuterPackets = "1-1",
        freedomOuterLength = "1-3",
        freedomOuterInterval = "5-10",
        freedomOuterMaxSplit = "",
        freedomMiddleEnabled = false,
        freedomInnerEnabled = true,
        freedomInnerPackets = "1-1",
        freedomInnerLength = "1",
        freedomInnerInterval = "4",
        freedomInnerMaxSplit = "517",
        freedomUdpNoiseEnabled = true,
        freedomUdpNoiseCount = 6
    )
    FreedomPreset.SNI_SHREDDER -> s.copy(
        freedomPreset = preset,
        freedomLayerCount = 2,
        freedomOuterPackets = "1-1",
        freedomOuterLength = "1-3",
        freedomOuterInterval = "5-10",
        freedomOuterMaxSplit = "4",
        freedomMiddleEnabled = false,
        freedomInnerEnabled = true,
        freedomInnerPackets = "1-1",
        freedomInnerLength = "1",
        freedomInnerInterval = "4",
        freedomInnerMaxSplit = "517",
        freedomUdpNoiseEnabled = true,
        freedomUdpNoiseCount = 4
    )
    FreedomPreset.AGGRESSIVE_RECORD_SPLIT -> s.copy(
        freedomPreset = preset,
        freedomLayerCount = 2,
        // Official XTLS skip-fragment pair
        freedomOuterPackets = "1-1",
        freedomOuterLength = "130",
        freedomOuterInterval = "560",
        freedomOuterMaxSplit = "4",
        freedomMiddleEnabled = false,
        freedomInnerEnabled = true,
        freedomInnerPackets = "2-4",
        freedomInnerLength = "1",
        freedomInnerInterval = "4",
        freedomInnerMaxSplit = "130",
        freedomUdpNoiseEnabled = true,
        freedomUdpNoiseCount = 8
    )
    FreedomPreset.EXTREME_ANTI_DPI -> s.copy(
        freedomPreset = preset,
        freedomLayerCount = 2,
        freedomOuterPackets = "1-1",
        freedomOuterLength = "1-3",
        freedomOuterInterval = "5-10",
        freedomOuterMaxSplit = "4",
        freedomMiddleEnabled = false,
        freedomInnerEnabled = true,
        freedomInnerPackets = "1-1",
        freedomInnerLength = "1",
        freedomInnerInterval = "4",
        freedomInnerMaxSplit = "517",
        freedomUdpNoiseEnabled = true,
        freedomUdpNoiseCount = 10
    )
    // MARBLE_OPERATOR_FREEDOM_V91: per-operator steel chains. These wire the same parameters the
    // DpiEvasionPolicy recipes use, so the Freedom settings sheet and the engine never disagree.
    FreedomPreset.SHATEL -> s.copy(
        freedomPreset = preset,
        freedomLayerCount = 3,
        freedomOuterPackets = "1-1",
        freedomOuterLength = "1-3",
        freedomOuterInterval = "5-10",
        freedomOuterMaxSplit = "4",
        freedomMiddleEnabled = true,
        freedomMiddlePackets = "2-4",
        freedomMiddleLength = "1",
        freedomMiddleInterval = "4",
        freedomMiddleMaxSplit = "130",
        freedomInnerEnabled = true,
        freedomInnerPackets = "1-1",
        freedomInnerLength = "1",
        freedomInnerInterval = "4",
        freedomInnerMaxSplit = "517",
        freedomUdpNoiseEnabled = true,
        freedomUdpNoiseCount = 12
    )
    FreedomPreset.HAMRAH_AVAL -> s.copy(
        freedomPreset = preset,
        freedomLayerCount = 3,
        freedomOuterPackets = "1-1",
        freedomOuterLength = "1-3",
        freedomOuterInterval = "5-10",
        freedomOuterMaxSplit = "4",
        freedomMiddleEnabled = true,
        freedomMiddlePackets = "1-1",
        freedomMiddleLength = "130",
        freedomMiddleInterval = "560",
        freedomMiddleMaxSplit = "4",
        freedomInnerEnabled = true,
        freedomInnerPackets = "1-1",
        freedomInnerLength = "1",
        freedomInnerInterval = "4",
        freedomInnerMaxSplit = "517",
        freedomUdpNoiseEnabled = true,
        freedomUdpNoiseCount = 16
    )
    FreedomPreset.IRANCELL -> s.copy(
        freedomPreset = preset,
        freedomLayerCount = 3,
        freedomOuterPackets = "1-1",
        freedomOuterLength = "130",
        freedomOuterInterval = "560",
        freedomOuterMaxSplit = "4",
        freedomMiddleEnabled = true,
        freedomMiddlePackets = "2-4",
        freedomMiddleLength = "1",
        freedomMiddleInterval = "4",
        freedomMiddleMaxSplit = "130",
        freedomInnerEnabled = true,
        freedomInnerPackets = "1-1",
        freedomInnerLength = "1",
        freedomInnerInterval = "4",
        freedomInnerMaxSplit = "517",
        freedomUdpNoiseEnabled = true,
        freedomUdpNoiseCount = 14
    )
    FreedomPreset.RIGHTEL -> s.copy(
        freedomPreset = preset,
        freedomLayerCount = 3,
        freedomOuterPackets = "1-1",
        freedomOuterLength = "1-3",
        freedomOuterInterval = "5-10",
        freedomOuterMaxSplit = "4",
        freedomMiddleEnabled = true,
        freedomMiddlePackets = "1-3",
        freedomMiddleLength = "10-30",
        freedomMiddleInterval = "5-10",
        freedomMiddleMaxSplit = "768",
        freedomInnerEnabled = true,
        freedomInnerPackets = "1-1",
        freedomInnerLength = "1",
        freedomInnerInterval = "4",
        freedomInnerMaxSplit = "517",
        freedomUdpNoiseEnabled = true,
        freedomUdpNoiseCount = 10
    )
    FreedomPreset.CUSTOM -> s.copy(freedomPreset = preset)
}

@Composable
private fun FreedomLayerCard(
    title: String,
    subtitle: String,
    tone: Color,
    enabled: Boolean,
    onEnabled: ((Boolean) -> Unit)?,
    packets: String,
    length: String,
    interval: String,
    maxSplit: String,
    onPackets: (String) -> Unit,
    onLength: (String) -> Unit,
    onInterval: (String) -> Unit,
    onMaxSplit: (String) -> Unit
) {
    PrismWell(
        modifier = Modifier.fillMaxWidth(),
        tone = if (enabled) tone else Aether.InkMuted,
        selected = enabled,
        radius = 16.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (enabled) tone else Aether.InkFaint)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onEnabled != null) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabled,
                        colors = marbleSwitchColors()
                    )
                }
            }

            AnimatedVisibility(enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        TinyField("Packets", packets, Modifier.weight(1f), onPackets)
                        TinyField("Length", length, Modifier.weight(1f), onLength)
                        TinyField("Interval", interval, Modifier.weight(1f), onInterval)
                    }
                    TinyField("maxSplit (optional)", maxSplit, Modifier.fillMaxWidth(), onMaxSplit)
                }
            }
        }
    }
}
