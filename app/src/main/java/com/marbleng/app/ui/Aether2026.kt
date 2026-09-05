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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.marbleng.app.AppRepository
import com.marbleng.app.R
import com.marbleng.app.core.AddressFamilyPolicy
import com.marbleng.app.core.GeoAssetIndex
import com.marbleng.app.core.RoutingEngine
import com.marbleng.app.core.RoutingPresets
import com.marbleng.app.core.BugSeverity
import com.marbleng.app.core.IranModeState
import com.marbleng.app.core.ManualConfigBuilder
import com.marbleng.app.core.ManualConfigDraft
import com.marbleng.app.core.ManualProtocol
import com.marbleng.app.core.ProtocolTally
import com.marbleng.app.core.QrCode
import com.marbleng.app.core.QrEcc
import com.marbleng.app.core.ServerCountry
import com.marbleng.app.core.ServersFilter
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import org.json.JSONArray
import org.json.JSONObject
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
    // MARBLE_DOCK_SCROLL_ONLY_V123 — a programmatic page turn (tab tap, Home routing focus) is
    // marked while it animates so the bottom dock never enters its glass state; only real
    // finger scrolls make the bar translucent.
    var tabTurn by remember { mutableStateOf(false) }
    BackHandler(enabled = detailProfile != null) { detailProfile = null }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // One suspension-safe page turn: the marker always clears — even when the animation is
    // cancelled by a finger grab — so the dock can never get stuck translucent.
    val goToTab: (Int) -> Unit = { page ->
        tabTurn = true
        scope.launch {
            try {
                pagerState.animateScrollToPage(page)
            } finally {
                tabTurn = false
            }
        }
    }

    // Sync pager → tab name for persistence and reset the dock to its resting skin when
    // switching pages. Each scrollable page reports its own motion below.
    LaunchedEffect(pagerState.currentPage) {
        tabTurn = false
        contentScrolling = false
        onContentScrollChanged(false)
        repo.rememberAppTab(tabs[pagerState.currentPage].name)
    }

    // Whichever coroutine owns the turn, any settled pager clears the marker, so a fast
    // double-tap or an interrupted animation can never leave the dock semi-transparent.
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) tabTurn = false
    }

    val reportContentScroll: (Boolean) -> Unit = { scrolling ->
        contentScrolling = scrolling
        onContentScrollChanged(scrolling)
    }

    // Routing focus from Home: jump to Settings tab
    LaunchedEffect(settingsFocus) {
        if (settingsFocus == "Routing") {
            goToTab(SpatialTab.SETTINGS.ordinal)
        }
    }

    // MARBLE_NO_IN_APP_NOTIFICATIONS_V121 — the product no longer raises in-app toasts.
    //
    // Every runtime message the engine produces is already visible where it belongs: connection
    // state on the connect button, failures under it, test outcomes on the node cards, import
    // results in the Servers list. The floating snackbar duplicated all of that on top of the
    // surface the user was already looking at, so it is gone. Messages are still recorded by the
    // repository (diagnostics, Bug Finder), they simply never interrupt the UI. System
    // notifications — the ongoing VPN notification and system alerts — are untouched.
    LaunchedEffect(repo.message, repo.busy) {
        if (!repo.busy && repo.message.isNotBlank()) repo.clearMessage()
    }

    // MARBLE_SIGNATURE_HOME_V112 — one shared deck truth: the Home page, the app-wide Signature
    // status banner and the floating connect button all read this exact evidence + action set.
    val deck = rememberDeckEvidence(repo)
    val deckCopyIp = rememberCopyIpAction(repo, deck.evidence.ip)

    // MARBLE_HOME_REDESIGN_V132 — the Home shortcut deck brings the two import entries a user
    // reaches for most (paste and QR) onto the connection surface, so adding a route no longer
    // requires a detour through the Servers tab. Both land in exactly the same smart intake the
    // Servers page uses, so a pasted subscription can never become a bogus proxy profile.
    val deckClipboard = LocalClipboardManager.current
    val deckQrImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) repo.importQrImage(uri, libraryIntakeTarget(repo))
    }
    val deckQrCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) repo.importQrBitmap(bitmap, libraryIntakeTarget(repo))
    }
    var deckQrSourceOpen by remember { mutableStateOf(false) }

    val deckActions = HomeActions(
        onToggleConnection = {
            with(deck.evidence) {
                when {
                    // A tunnel that is already closing ignores the button until it has closed.
                    disconnecting -> Unit
                    connected || connecting || blocked -> repo.stopVpn()
                    // The selected server is the one the button acts on; reconnectLastOrAuto
                    // resolves it (selection is persisted with the same reference).
                    else -> repo.reconnectLastOrAuto(onConnect)
                }
            }
        },
        onCopyIp = deckCopyIp,
        onRefreshIp = { repo.refreshServerIntel(deck.profile, force = true) },
        onIpDetails = { ipDetailsOpen = true },
        // MARBLE_HOME_V137 — one ping entry: the live tunnel ladder while connected, the
        // selected server's endpoint otherwise. Same method, same readout, every state.
        onTestPing = { repo.measureHomePing() },
        onLibrary = { goToTab(SpatialTab.LIBRARY.ordinal) },
        onConnectProfile = { profile -> onConnect(profile) },
        onAddRoute = { goToTab(SpatialTab.LIBRARY.ordinal) },
        onRank = { repo.smartRank() },
        onPrivacy = {
            repo.audit()
            dialog = "Privacy"
        },
        onRouting = {
            settingsFocus = "Routing"
            goToTab(SpatialTab.SETTINGS.ordinal)
        },
        onTests = { goToTab(SpatialTab.SETTINGS.ordinal) },
        onPasteImport = {
            val pasted = deckClipboard.getText()?.text.orEmpty()
            if (pasted.isBlank()) {
                repo.setRuntimeMessage("Clipboard is empty")
            } else {
                repo.importClipboard(pasted, libraryIntakeTarget(repo))
            }
        },
        onQrImport = { deckQrSourceOpen = true }
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
            // MARBLE_HOME_GRADIENTS_V116 — the whole page follows the selected Home style's
            // multi-colour identity, so Home, Servers and Settings share the same professional
            // gradient family instead of one grey wash.
            DeepSpaceBackdrop(
                Modifier.matchParentSize(),
                flavor = homeFlavorFor(parseHomeStyle(repo.settings.homeStyle))
            )

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

            // MARBLE_FLOATING_DOCK_V117 — the dock is a true overlay: no Scaffold slot
            // reserves space for it, so pages and the backdrop keep scrolling beneath the
            // glass and shine through it (per-tab lists pad their last item past it).
            // MARBLE_DOCK_SCROLL_ONLY_V123 — glass belongs to real scrolling only. A tap on a
            // tab is a programmatic page turn, so the turn marker keeps the bar opaque for the
            // whole tap animation; content scroll and finger-dragged turns still fade it.
            val glass = contentScrolling || pagerState.isScrollInProgress
            FloatingSpatialDock(
                selected = tabs[pagerState.currentPage],
                glass = glass && !tabTurn,
                onSelect = { next ->
                    detailProfile = null
                    settingsFocus = null
                    goToTab(next.ordinal)
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

            // MARBLE_HOME_REDESIGN_V132 — the QR entry of the Home shortcut deck. One entry,
            // two ways in: a live camera scan or a saved image.
            if (deckQrSourceOpen) {
                AlertDialog(
                    onDismissRequest = { deckQrSourceOpen = false },
                    containerColor = Aether.VoidElevated,
                    shape = ServersCardShape,
                    title = { Text(trx("Add from QR code"), color = Aether.Ink) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CyberButton(
                                label = "Scan with camera",
                                color = Aether.Emerald,
                                variant = PrismButtonVariant.Primary,
                                icon = HomeIcon.CAMERA,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                deckQrSourceOpen = false
                                deckQrCameraLauncher.launch(null)
                            }
                            CyberButton(
                                label = "Pick from gallery",
                                color = Aether.Amethyst,
                                icon = HomeIcon.QR,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                deckQrSourceOpen = false
                                deckQrImageLauncher.launch("image/*")
                            }
                            Text(
                                trx("Point the camera at the code, or pick a screenshot or photo."),
                                color = Aether.InkFaint,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        MarbleDialogAction(
                            label = "Cancel",
                            tone = Aether.InkMuted,
                            onClick = { deckQrSourceOpen = false }
                        )
                    }
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
            "Unsupported VLESS • pick a server with TLS/REALITY"
        "failed to build outbound config" in lower ->
            "Xray rejected this server configuration • check protocol/TLS settings"
        "context deadline exceeded" in lower && "dns-query" in lower ->
            "DNS resolver timed out • Marble is switching to a fallback path"
        message.length > 260 -> message.take(257) + "…"
        else -> message
    }
}


@Composable
private fun DeepSpaceBackdrop(
    modifier: Modifier = Modifier,
    flavor: HomeFlavor = HomeFlavor.IOS_SLIDER
) {
    // Clean iOS backdrop without starfield or cosmic blobs
    Box(modifier) {
        PrismBackdrop(Modifier.matchParentSize(), flavor)
    }
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
    // MARBLE_FLOATING_DOCK_V117 / MARBLE_DOCK_STILL_BAR_V132
    //  - rendered as an overlay (no Scaffold bottomBar slot), so pages scroll under it
    //  - never flush with the screen edge: side margins + a lift above the gesture bar
    //  - THE BAR ITSELF NEVER MOVES. Earlier revisions breathed the whole dock up and down on
    //    the shared frame clock and shrank it by 1.5% during a page turn. On a real screen that
    //    read as a wobble under the thumb, so every ambient transform is gone: no translation,
    //    no scale, no selection pulsation. Only colour, shadow depth and the selection wash
    //    animate, and all three use overshoot-free tweens.
    //  - MARBLE_DOCK_NIGHT_FLASH_V132 — the glass sheen used to be written as
    //    `Aether.BarGlassHighlight.copy(alpha = highlightAlpha)`. `copy(alpha = …)` REPLACES the
    //    token's own alpha with the raw 0..1 animation value, so the moment a tab was tapped
    //    (a tap sets `pagerState.isScrollInProgress`, which switches glass on) the AMOLED bar
    //    was painted with a full-opacity ice-blue #ADD8E6 band across its top — the "the bar
    //    turns white when I tap it in night/AMOLED" bug. Every overlay now scales the token's
    //    own alpha, exactly like the surface and the border already did.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val barShape = RoundedCornerShape(28.dp)

        val glassFraction by animateFloatAsState(
            targetValue = if (glass) 1f else 0f,
            animationSpec = MarbleMotionSpecs.DockFloat,
            label = "dock-glass-fraction"
        )

        val idleSurface = Aether.VoidElevated
        val glassSurface = Aether.BarGlass
        val surfaceAlpha by animateFloatAsState(
            targetValue = if (glass) 0.78f else 1f,
            animationSpec = MarbleMotionSpecs.DockFloat,
            label = "dock-surface-alpha"
        )
        // MARBLE_DOCK_SCROLL_ONLY_V123 — the idle<->glass surface is a continuous colour blend,
        // not a 50% switch: the old midpoint jump was visible as a hard snap while the spring
        // was still animating. Both endpoints and everything between now interpolate smoothly.
        val dockSurface = lerp(
            idleSurface,
            glassSurface.copy(alpha = glassSurface.alpha * surfaceAlpha),
            glassFraction
        )

        val highlightAlpha by animateFloatAsState(
            targetValue = if (glass) 1f else 0f,
            animationSpec = MarbleMotionSpecs.DockFloat,
            label = "dock-highlight"
        )

        // Depth is the only thing that changes size-wise while scrolling, and a shadow does not
        // move the surface itself, so the bar keeps its exact footprint on the screen.
        val dockElevation by animateDpAsState(
            targetValue = if (glass) 20.dp else 14.dp,
            animationSpec = MarbleMotionSpecs.DockDp,
            label = "dock-elevation"
        )

        val borderAlpha by animateFloatAsState(
            targetValue = if (glass) 0.85f else 1f,
            animationSpec = MarbleMotionSpecs.DockFloat,
            label = "dock-border-alpha"
        )

        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = dockElevation,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
                .clip(barShape)
                .background(dockSurface)
                .then(
                    if (highlightAlpha > 0.01f) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    Aether.BarGlassHighlight.copy(
                                        alpha = Aether.BarGlassHighlight.alpha * highlightAlpha
                                    ),
                                    Color.Transparent
                                )
                            )
                        )
                    } else Modifier
                )
                .border(
                    1.dp,
                    Aether.BarGlassBorder.copy(alpha = Aether.BarGlassBorder.alpha * borderAlpha),
                    barShape
                )
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpatialTab.entries.forEach { item ->
                val active = item == selected
                val glassShape = RoundedCornerShape(20.dp)

                // MARBLE_DOCK_STABLE_COLOR_V115 — a spring interpolates past its target on the
                // way in (underdamped) and that overshoot flashed the pill/text on every click
                // and theme switch, so the chrome animates with a short overshoot-free tween.
                val inkTone by animateColorAsState(
                    targetValue = if (active) Aether.Cyan else Aether.InkMuted,
                    animationSpec = MarbleMotionSpecs.DockColor,
                    label = "dock-tone-${item.name}"
                )
                val pillBg by animateColorAsState(
                    targetValue = if (active) Aether.Cyan.copy(alpha = .16f) else Color.Transparent,
                    animationSpec = MarbleMotionSpecs.DockColor,
                    label = "dock-pill-${item.name}"
                )
                val indicatorTone by animateColorAsState(
                    targetValue = if (active) Aether.Cyan.copy(alpha = .34f) else Color.Transparent,
                    animationSpec = MarbleMotionSpecs.DockColor,
                    label = "dock-indicator-${item.name}"
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(glassShape)
                        .background(pillBg)
                        .border(1.dp, indicatorTone, glassShape)
                        .kineticClickable(
                            boundedShape = glassShape,
                            role = Role.Tab,
                            pressScale = .98f,
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

// MARBLE_HOME_VECTOR_ICONS_V36
private enum class HomeIcon {
    BRAND, POWER, STOP, CANCEL, RESET, SHIELD, TUNNEL, ROUTE,
    PING, JITTER, QUALITY, NODES, VERIFIED, CHECK, MODE, BENCHMARK,
    RANK, LIBRARY, PRIVACY, ROUTING, NETWORK, SERVER, DOWNLOAD, UPLOAD,
    DETAILS, SPARK, STATUS, MORE, MENU,
    // MARBLE_SERVERS_V114 — glyphs the rebuilt Servers screen and the Settings hub need:
    // a clipboard for the floating magic button, an information mark, a palette for the
    // theme preview, a globe for language/links, disclosure chevrons, a back arrow and a
    // typeface mark for the font picker.
    CLIPBOARD, INFO, PALETTE, GLOBE, CHEVRON, BACK, FONT,
    // MARBLE_SERVERS_STYLE_CARDS_V117 — a quiet trash glyph used inside the per-server
    // overflow sheet, keeping the row itself clean and minimal.
    TRASH,
    // MARBLE_SERVERS_REDESIGN_V120 — the rebuilt Servers screen: add (+), search, sort,
    // advanced filter, share/export, QR, folder (move), pencil (edit) and the tick that
    // marks the selected row of a rounded dropdown.
    PLUS, SEARCH, SORT, FILTER, SHARE, QR, FOLDER, PENCIL, ACTIVE, CAMERA
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
        // MARBLE_ICON_POLISH_V115 — rounded caps AND joins: modern product icon strokes never
        // leave miter spikes at corners, so every glyph reads clean at 14dp and at 40dp.
        val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val fineLine = Stroke(width = fine, cap = StrokeCap.Round, join = StrokeJoin.Round)

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

            HomeIcon.TRASH -> {
                // Lid, handle and a rounded bin: one glance, no ambiguity.
                drawLine(color, Offset(w*.27f, h*.31f), Offset(w*.73f, h*.31f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.43f, h*.24f), Offset(w*.57f, h*.24f), fine, StrokeCap.Round)
                val bin = Path().apply {
                    moveTo(w*.32f, h*.34f)
                    lineTo(w*.355f, h*.76f)
                    quadraticBezierTo(w*.37f, h*.84f, w*.45f, h*.84f)
                    lineTo(w*.55f, h*.84f)
                    quadraticBezierTo(w*.63f, h*.84f, w*.645f, h*.76f)
                    lineTo(w*.68f, h*.34f)
                    close()
                }
                drawPath(bin, color, style = fineLine)
            }

            HomeIcon.PLUS -> {
                drawLine(color, Offset(w*.5f, h*.24f), Offset(w*.5f, h*.76f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.24f, h*.5f), Offset(w*.76f, h*.5f), stroke, StrokeCap.Round)
            }

            HomeIcon.SEARCH -> {
                drawCircle(color, radius = m*.27f, center = Offset(w*.43f, h*.43f), style = line)
                drawLine(color, Offset(w*.62f, h*.62f), Offset(w*.8f, h*.8f), stroke, StrokeCap.Round)
            }

            HomeIcon.SORT -> {
                // Three descending rules: "reorder, longest first".
                drawLine(color, Offset(w*.24f, h*.29f), Offset(w*.76f, h*.29f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.24f, h*.5f), Offset(w*.62f, h*.5f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.24f, h*.71f), Offset(w*.48f, h*.71f), stroke, StrokeCap.Round)
            }

            HomeIcon.FILTER -> {
                // A funnel: broad at the mouth, narrowing to a stem.
                val funnel = Path().apply {
                    moveTo(w*.2f, h*.26f)
                    lineTo(w*.8f, h*.26f)
                    lineTo(w*.56f, h*.55f)
                    lineTo(w*.56f, h*.78f)
                    lineTo(w*.44f, h*.78f)
                    lineTo(w*.44f, h*.55f)
                    close()
                }
                drawPath(funnel, color, style = line)
            }

            HomeIcon.SHARE -> {
                // Lift out of a tray: export / copy-link.
                val tray = Path().apply {
                    moveTo(w*.27f, h*.56f)
                    lineTo(w*.27f, h*.71f)
                    quadraticBezierTo(w*.27f, h*.79f, w*.35f, h*.79f)
                    lineTo(w*.65f, h*.79f)
                    quadraticBezierTo(w*.73f, h*.79f, w*.73f, h*.71f)
                    lineTo(w*.73f, h*.56f)
                }
                drawPath(tray, color, style = fineLine)
                drawLine(color, Offset(w*.5f, h*.62f), Offset(w*.5f, h*.23f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.37f, h*.36f), Offset(w*.5f, h*.23f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.63f, h*.36f), Offset(w*.5f, h*.23f), stroke, StrokeCap.Round)
            }

            HomeIcon.QR -> {
                // Three finder squares and one solid block: unmistakably a QR code.
                listOf(
                    Offset(w*.18f, h*.18f) to Offset(w*.38f, h*.38f),
                    Offset(w*.62f, h*.18f) to Offset(w*.82f, h*.38f),
                    Offset(w*.18f, h*.62f) to Offset(w*.38f, h*.82f)
                ).forEach { (top, bottom) ->
                    drawRect(
                        color = color,
                        topLeft = top,
                        size = Size(bottom.x - top.x, bottom.y - top.y),
                        style = fineLine
                    )
                }
                drawRect(
                    color = color,
                    topLeft = Offset(w*.62f, h*.62f),
                    size = Size(w*.2f, h*.2f)
                )
            }

            HomeIcon.FOLDER -> {
                val folder = Path().apply {
                    moveTo(w*.18f, h*.36f)
                    lineTo(w*.18f, h*.28f)
                    quadraticBezierTo(w*.18f, h*.22f, w*.26f, h*.22f)
                    lineTo(w*.42f, h*.22f)
                    lineTo(w*.5f, h*.3f)
                    lineTo(w*.74f, h*.3f)
                    quadraticBezierTo(w*.82f, h*.3f, w*.82f, h*.38f)
                    lineTo(w*.82f, h*.7f)
                    quadraticBezierTo(w*.82f, h*.78f, w*.74f, h*.78f)
                    lineTo(w*.26f, h*.78f)
                    quadraticBezierTo(w*.18f, h*.78f, w*.18f, h*.7f)
                    close()
                }
                drawPath(folder, color, style = fineLine)
            }

            HomeIcon.PENCIL -> {
                // Body, nib and the stroke it leaves behind.
                drawLine(color, Offset(w*.27f, h*.73f), Offset(w*.66f, h*.34f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.66f, h*.34f), Offset(w*.76f, h*.24f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.22f, h*.78f), Offset(w*.35f, h*.75f), fine, StrokeCap.Round)
            }

            HomeIcon.ACTIVE -> {
                drawLine(color, Offset(w*.26f, h*.53f), Offset(w*.43f, h*.7f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.43f, h*.7f), Offset(w*.76f, h*.3f), stroke, StrokeCap.Round)
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

            HomeIcon.MENU -> {
                // Three round-capped bars: an unmistakable hamburger at any size.
                drawLine(color, Offset(w*.20f, h*.30f), Offset(w*.80f, h*.30f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.20f, h*.50f), Offset(w*.80f, h*.50f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.20f, h*.70f), Offset(w*.80f, h*.70f), stroke, StrokeCap.Round)
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

            // MARBLE_PING_WIFI_V122 — a confident wifi radiate (three bold arcs around a solid
            // dot, all centred on one pivot) with a small speed-needle tick at the lower right,
            // so the glyph reads as "latency + speed" even at 13dp instead of two hairlines
            // around a speck.
            HomeIcon.PING -> {
                val pivotX = w * .40f
                val pivotY = h * .66f
                drawArc(color,205f,130f,false,Offset(pivotX-m*.14f,pivotY-m*.14f),Size(m*.28f,m*.28f),style=line)
                drawArc(color,205f,130f,false,Offset(pivotX-m*.26f,pivotY-m*.26f),Size(m*.52f,m*.52f),style=line)
                drawArc(color,205f,130f,false,Offset(pivotX-m*.37f,pivotY-m*.37f),Size(m*.74f,m*.74f),style=line)
                drawCircle(color, m*.085f, Offset(pivotX,pivotY))
                // Speed needle: a short rising tick with a round foot at the lower right.
                drawLine(color, Offset(w*.62f, h*.88f), Offset(w*.82f, h*.62f), stroke, StrokeCap.Round)
                drawCircle(color, (stroke * .62f).coerceAtLeast(1.2f), Offset(w*.62f, h*.88f))
            }

            HomeIcon.CAMERA -> {
                // Body, lens and viewfinder bump: a camera at any size.
                val bump = Path().apply {
                    moveTo(w*.36f, h*.32f)
                    lineTo(w*.41f, h*.23f)
                    lineTo(w*.59f, h*.23f)
                    lineTo(w*.64f, h*.32f)
                }
                drawPath(bump, color, style = fineLine)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w*.16f, h*.32f),
                    size = Size(w*.68f, h*.46f),
                    cornerRadius = CornerRadius(m*.09f, m*.09f),
                    style = fineLine
                )
                drawCircle(color, m*.11f, Offset(w*.50f, h*.55f), style = line)
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
    actionVariant: PrismButtonVariant = PrismButtonVariant.Secondary,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // MARBLE_PAGE_HEADER_V117 — a slim, quiet page header shared by Servers and Settings.
    Row(
        modifier=modifier
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
                variant=actionVariant,
                onClick=onAction
            )
        }
    }
}

/**
 * MARBLE_SERVERS_MENU_V117 — the app's own hamburger menu surface. A small anchored panel below the
 * page header, in the same material language as the rest of MarbleNG (Prism tokens, one hairline,
 * no foreign sheet). It opens from the header hamburger button and closes on scrim tap/back.
 */
@Composable
private fun MarbleMenuPanel(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = open) { onClose() }
    AnimatedVisibility(
        visible = open,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(MarbleMotionSpecs.ResponseFloat),
        exit = fadeOut(MarbleMotionSpecs.ExitFloat)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .38f))
                    .clickable(enabled = open, onClick = onClose)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(start = 14.dp, end = 16.dp, top = 60.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 22.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = .28f),
                        spotColor = Color.Black.copy(alpha = .34f)
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(Aether.VoidElevated)
                    .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            trx(title),
                            color = Aether.Ink,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Menus",
                            color = Aether.InkMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    content()
                }
            }
        }
    }
}

/** One row in [MarbleMenuPanel]: a small tinted icon chip, title and one-line detail. */
@Composable
private fun MarbleMenuPanelItem(
    title: String,
    subtitle: String,
    icon: HomeIcon,
    tone: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Aether.GlassStrong.copy(alpha = .32f))
            .border(1.dp, Aether.GlassBorderSoft.copy(alpha = .55f), shape)
            .kineticClickable(
                role = Role.Button,
                boundedShape = shape,
                showIndication = false,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tone.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) {
            HomeVectorIcon(icon, tone, Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                trx(title),
                color = Aether.Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                trx(subtitle),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    // MARBLE_SELECT_IS_NOT_CONNECT_V121 — Home follows, in order: the route actually carrying
    // traffic, then the server the user selected on the Servers page, then the remembered one.
    // Reading the selection state here is what makes a tap on a server move Home immediately.
    val active = repo.profile(
        repo.activeProfileId,
        repo.activeProfileSourceId
    )
        ?: repo.profile(repo.selectedProfileId, repo.selectedProfileSourceId)
        ?: repo.lastProfile()
    // Identity always comes from the selected profile, never from the runtime state detail
    // string (which carries engine progress copy and is not a node name).
    val activeName = active?.name ?: "Choose a route"
    val endpoint = active?.host?.trim()?.removeSurrounding("[", "]").orEmpty()
    val serverInfo = repo.serverIntel?.takeIf {
        endpoint.isNotBlank() && it.endpoint.equals(endpoint, ignoreCase = true)
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

@Composable
@Suppress("UNUSED_PARAMETER")
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

    LaunchedEffect(Unit) { onContentScrollChanged(false) }
    LaunchedEffect(connected, active?.id, active?.subscriptionId, endpoint) {
        if (connected && active != null && endpoint.isNotBlank()) {
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

    // MARBLE_CONNECT_BUTTON_V121 — the chosen connection-button silhouette reaches every Home
    // presentation through one composition local, so each style renders the same button without
    // reading Settings itself.
    val connectButtonStyle = parseConnectButtonStyle(repo.settings.connectButtonStyle)

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalConnectButtonStyle provides connectButtonStyle) {
            HomeStyleSurface(
                style = parseHomeStyle(repo.settings.homeStyle),
                evidence = evidence,
                actions = actions,
                bottomClearance = dockClearance(),
                pro = pro,
                // MARBLE_DOCK_SCROLL_ONLY_V123 — the Home page reports its own scrolling so the
                // dock turns to glass exactly when content moves under it.
                onScrollChanged = onContentScrollChanged,
                repo = repo
            )
        }

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
 * MARBLE_SIGNATURE_HOME_V112 — resolves the Signature studio snapshot from the current settings.
 *
 * MARBLE_SIGNATURE_STUDIO_TRIM_V121 — the snapshot no longer carries a server rail or a style
 * switcher: routes are chosen on the Servers page and the presentation in Settings, so Home is a
 * single connection surface and this context is only the studio's own chrome.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun rememberSignatureProContext(
    repo: AppRepository,
    deck: DeckEvidence
): HomeProContext = HomeProContext(
    showBanner = repo.settings.proStatusBannerEnabled,
    showCornerActions = repo.settings.proCornerActionsEnabled,
    shortcut = parseProShortcut(repo.settings.proShortcut),
    accent = parseProAccent(repo.settings.proAccent)
)

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
// SERVERS
// =================================================================================================

// =============================================================================
// MARBLE_SERVERS_REDESIGN_V120 — the Servers page is rebuilt around a card
// system: one page header with a headline title and two round controls, one
// pill search field with a filter rail under it, one collapsible header per
// subscription (or per country when grouping is on) and one independent card
// per server. Every menu is a Material 3 dropdown on a 16dp radius; every
// colour comes from the active theme palette, so the page follows the Solid
// White, Dark and System themes without a single hardcoded value.
//
// The page owns no business rules of its own. What is visible is decided by
// [ServersFilter] + [ServersQuery] (unit tested) and what a server can be built
// from is decided by [ManualConfigBuilder.missingRequirement] (unit tested), so
// the UI cannot drift from the engine behind it.
// =============================================================================

private enum class LibraryGroupKind { SUBSCRIPTION, MANUAL, COUNTRY }

private data class LibraryGroup(
    /** Subscription id for source groups, country code (or "") for country groups. */
    val key: String,
    val title: String,
    val kind: LibraryGroupKind,
    val flag: String = "",
    val profiles: List<ProxyProfile>
)

/** One entry of the protocol dropdown on the Add-node page. */
private data class AddNodeProtocol(
    val label: String,
    /** The Marble builder that produces this protocol, or null when the core cannot carry it. */
    val target: ManualProtocol?,
    val note: String = ""
)

/**
 * Every protocol the Add-node form offers.
 *
 * Marble builds a config only for protocols its Xray-based core can actually dial, so the entries
 * without a [AddNodeProtocol.target] render as disabled rows with an honest reason instead of
 * letting a user fill a form that could never connect.
 */
private val ADD_NODE_PROTOCOLS: List<AddNodeProtocol> = listOf(
    AddNodeProtocol("VLESS", ManualProtocol.VLESS),
    AddNodeProtocol("VMess", ManualProtocol.VMESS),
    AddNodeProtocol("Trojan", ManualProtocol.TROJAN),
    AddNodeProtocol("Shadowsocks", ManualProtocol.SHADOWSOCKS),
    AddNodeProtocol("Hysteria2", ManualProtocol.HYSTERIA2),
    AddNodeProtocol("Hysteria", null, "Not supported by this core"),
    AddNodeProtocol("TUIC v5", null, "Not supported by this core"),
    AddNodeProtocol("WireGuard", ManualProtocol.WIREGUARD),
    AddNodeProtocol("AmneziaWG", null, "Not supported by this core"),
    AddNodeProtocol("MASQUE", null, "Not supported by this core"),
    AddNodeProtocol("OpenVPN", null, "Not supported by this core"),
    AddNodeProtocol("SSH", ManualProtocol.SSH),
    AddNodeProtocol("SOCKS5", ManualProtocol.SOCKS5),
    AddNodeProtocol("HTTP", ManualProtocol.HTTP),
    AddNodeProtocol("HTTPS", ManualProtocol.HTTPS),
    AddNodeProtocol("Xray JSON", ManualProtocol.XRAY_JSON)
)

private val SERVERS_TRANSPORTS = listOf("raw", "websocket", "xhttp", "grpc", "httpupgrade", "mkcp")
private val SERVERS_FLOWS = listOf("", "xtls-rprx-vision", "xtls-rprx-origin")
private val SERVERS_FINGERPRINTS = listOf("chrome", "firefox", "safari", "randomized", "unsafe")

/** AEAD methods the bundled Xray core accepts for a Shadowsocks outbound. */
private val SERVERS_SS_METHODS = listOf(
    "aes-128-gcm",
    "aes-256-gcm",
    "chacha20-poly1305",
    "xchacha20-poly1305",
    "2022-blake3-aes-128-gcm",
    "2022-blake3-aes-256-gcm",
    "2022-blake3-chacha20-poly1305",
    "none"
)

/** Shared silhouettes: one card radius, one menu radius, one badge radius, one pill. */
private val ServersCardShape = RoundedCornerShape(16.dp)
private val ServersMenuShape = RoundedCornerShape(16.dp)
private val ServersBadgeShape = RoundedCornerShape(8.dp)
private val ServersPillShape = RoundedCornerShape(999.dp)

// MARBLE_SERVERS_STACKED_GROUPS_V121 — the two halves of a subscription box.
//
// A group is a single card: the header rounds only its top, every server row below it is square,
// and the last row rounds only the bottom. Nothing between them is rounded or spaced, so the
// servers of a subscription read as one continuous stack instead of a pile of separate cards.
private val ServersGroupHeadShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val ServersGroupTailShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
private val ServersGroupBodyShape = RoundedCornerShape(0.dp)

/**
 * Draws the box hairline of one slice of a stacked group.
 *
 * The frame is a single rounded rectangle stroke that is deliberately extended past the slice on
 * whichever side continues (`openTop` / `openBottom`) and clipped away there, so neighbouring
 * slices contribute one continuous outline with no doubled hairline at the seams.
 */
private fun Modifier.serversStackedFrame(
    openTop: Boolean = false,
    openBottom: Boolean = false,
    color: Color,
    width: Dp = 1.dp,
    radius: Dp = 16.dp
): Modifier = drawBehind {
    val stroke = width.toPx()
    val r = radius.toPx()
    val top = if (openTop) -r * 2f else 0f
    val bottom = if (openBottom) size.height + r * 2f else size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, top + stroke / 2f),
        size = Size(size.width - stroke, bottom - top - stroke),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = stroke)
    )
}

/** MARBLE_MANUAL_BUCKET_V122 — pasted/imported configs always land in the permanent Manual bucket. */
private fun libraryIntakeTarget(@Suppress("UNUSED_PARAMETER") repo: AppRepository): String = "manual"

/** Subscription accounting, straight from the provider's subscription-userinfo headers. */
private fun subscriptionDataText(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun subscriptionUsageText(sub: Subscription): String =
    "${subscriptionDataText(sub.uploadBytes + sub.downloadBytes)} / " +
        if (sub.totalBytes <= 0L) "\u221E" else subscriptionDataText(sub.totalBytes)

private fun subscriptionExpiryText(sub: Subscription): String {
    if (sub.expireAt <= 0L) return "Unlimited"
    val formatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
    val expired = sub.expireAt < System.currentTimeMillis()
    return if (expired) "Expired" else formatter.format(java.util.Date(sub.expireAt))
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
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val settings = repo.settings

    var search by rememberSaveable { mutableStateOf("") }
    // MARBLE_ADD_SERVER_MENU_V121 — the + button opens a menu; `addForm` holds which authoring
    // form the user asked for (null = no form open).
    var addMenuOpen by remember { mutableStateOf(false) }
    var addForm by remember { mutableStateOf<String?>(null) }
    val qrImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) repo.importQrImage(uri, libraryIntakeTarget(repo))
    }
    // MARBLE_QR_CAMERA_V122 — the live camera frame is decoded on a worker inside the repo.
    // TakePicturePreview delegates to the system camera app, so no CAMERA permission and no
    // FileProvider are needed.
    val qrCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) repo.importQrBitmap(bitmap, libraryIntakeTarget(repo))
    }
    var qrSourceOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var groupMenuOpen by remember { mutableStateOf(false) }
    var protocolMenuOpen by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    var allFiltersOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var qrTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var manageSubscription by remember { mutableStateOf<Subscription?>(null) }
    var editSubscriptionName by remember { mutableStateOf("") }
    var editSubscriptionUrl by remember { mutableStateOf("") }
    var deleteSubscription by remember { mutableStateOf<Subscription?>(null) }
    var pruneFailedTarget by remember { mutableStateOf<Pair<Subscription, String>?>(null) }

    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    val benchmarks = repo.benchmarks.associateBy { it.profileId }
    val filter = ServersFilter(
        query = search,
        protocol = settings.serversProtocolFilter,
        sourceId = repo.librarySourceFilter,
        onlyReachable = settings.serversOnlyReachable,
        maxPingMs = settings.serversMaxPingMs,
        groupByCountry = settings.serversGroupByCountry
    )
    val allProfiles = repo.libraryProfiles
    val visibleProfiles = ServersQuery.sort(
        profiles = ServersQuery.visible(allProfiles, filter, benchmarks),
        mode = settings.nodeSortMode,
        reverse = settings.nodeSortReverse,
        benchmarks = benchmarks
    )
    val protocolTallies = ServersQuery.protocolTallies(
        allProfiles.filter { it.subscriptionId == filter.sourceId || filter.sourceId == "all" }
    )

    // Groups: one module per subscription (or per country when the switch is on). A source with no
    // match still renders while nothing is being searched, so a fresh source stays reachable.
    val groups: List<LibraryGroup> = if (settings.serversGroupByCountry) {
        ServersQuery.groupByCountry(visibleProfiles).map { bucket ->
            LibraryGroup(
                key = "country:${bucket.country.code.ifBlank { "unknown" }}",
                title = bucket.country.name,
                kind = LibraryGroupKind.COUNTRY,
                flag = bucket.country.flag,
                profiles = bucket.profiles
            )
        }
    } else {
        buildList {
            repo.subscriptions.forEach { sub ->
                val nodes = visibleProfiles.filter { it.subscriptionId == sub.id }
                if (nodes.isNotEmpty() || (search.isBlank() && filter.sourceId == "all")) {
                    add(LibraryGroup(sub.id, sub.name, LibraryGroupKind.SUBSCRIPTION, profiles = nodes))
                }
            }
            // MARBLE_MANUAL_BUCKET_V122 — the local bucket is permanent, never gated.
            val manualNodes = visibleProfiles.filter { it.subscriptionId == "manual" }
            if (manualNodes.isNotEmpty()) {
                add(LibraryGroup("manual", "Manual", LibraryGroupKind.MANUAL, profiles = manualNodes))
            }
        }
    }

    val activeGroupLabel = when (filter.sourceId) {
        "all" -> "All groups"
        "manual" -> "Manual"
        else -> repo.subscriptions.firstOrNull { it.id == filter.sourceId }?.name ?: "All groups"
    }

    val groupOptions: List<Pair<String, String>> = buildList {
        add("all" to "All groups")
        repo.subscriptions.forEach { add(it.id to it.name) }
        add("manual" to "Manual")
    }

    val updateSettings: (AppSettings.() -> AppSettings) -> Unit = { change ->
        repo.updateSettings(settings.change())
    }

    // ---------------------------------------------------------------- dialogs

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text(trx("Edit server"), color = Aether.Ink) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(trx("Display name")) },
                    singleLine = true,
                    shape = ServersCardShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = marbleOutlinedTextFieldColors()
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
                MarbleDialogAction(
                    label = "Cancel",
                    tone = Aether.InkMuted,
                    onClick = { renameTarget = null }
                )
            }
        )
    }

    moveTarget?.let { target ->
        ServersMoveDialog(
            profile = target,
            repo = repo,
            onPick = { targetSourceId ->
                repo.moveProfile(target.id, target.subscriptionId, targetSourceId)
                moveTarget = null
            },
            onDismiss = { moveTarget = null }
        )
    }

    qrTarget?.let { target ->
        ServersQrDialog(
            profile = target,
            onShare = { text ->
                clipboard.setText(AnnotatedString(text))
                repo.setRuntimeMessage("Config link copied")
            },
            onDismiss = { qrTarget = null }
        )
    }

    // MARBLE_QR_CAMERA_V122 — one entry, two ways in: a live camera scan or a saved image.
    if (qrSourceOpen) {
        AlertDialog(
            onDismissRequest = { qrSourceOpen = false },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text(trx("Add from QR code"), color = Aether.Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CyberButton(
                        label = "Scan with camera",
                        color = Aether.Emerald,
                        variant = PrismButtonVariant.Primary,
                        icon = HomeIcon.CAMERA,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        qrSourceOpen = false
                        qrCameraLauncher.launch(null)
                    }
                    CyberButton(
                        label = "Pick from gallery",
                        color = Aether.Amethyst,
                        icon = HomeIcon.QR,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        qrSourceOpen = false
                        qrImportLauncher.launch("image/*")
                    }
                    Text(
                        trx("Point the camera at the code, or pick a screenshot or photo."),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                MarbleDialogAction(
                    label = "Cancel",
                    tone = Aether.InkMuted,
                    onClick = { qrSourceOpen = false }
                )
            }
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text(trx("Delete server?"), color = Aether.Danger) },
            text = {
                Text(
                    "Remove ${stripLeadingFlag(profile.name)} from ${profile.subscriptionName}? " +
                        trx("Deleting is confirmed here, never silent."),
                    color = Aether.InkMuted
                )
            },
            confirmButton = {
                MarbleDialogAction(
                    label = "Delete",
                    tone = Aether.Danger,
                    enabled = !repo.busy,
                    onClick = {
                        // Source-aware: the same id can live in two sources at once.
                        repo.removeProfile(profile.id, profile.subscriptionId)
                        deleteTarget = null
                    }
                )
            },
            dismissButton = {
                MarbleDialogAction(
                    label = "Cancel",
                    tone = Aether.InkMuted,
                    onClick = { deleteTarget = null }
                )
            }
        )
    }

    manageSubscription?.let { target ->
        ServersSubscriptionDialog(
            subscription = target,
            repo = repo,
            nameDraft = editSubscriptionName,
            urlDraft = editSubscriptionUrl,
            onNameChange = { editSubscriptionName = it },
            onUrlChange = { editSubscriptionUrl = it },
            onPrune = { kind ->
                pruneFailedTarget = target to kind
                manageSubscription = null
            },
            onDelete = {
                deleteSubscription = target
                manageSubscription = null
            },
            onDismiss = { manageSubscription = null }
        )
    }

    pruneFailedTarget?.let { request ->
        val target = request.first
        val kind = request.second
        val failedCount = repo.failedSubscriptionNodeCount(target.id, kind)
        AlertDialog(
            onDismissRequest = { pruneFailedTarget = null },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text("Remove failed $kind servers?", color = Aether.Danger) },
            text = {
                Text(
                    "This removes $failedCount failed server" +
                        (if (failedCount == 1) "" else "s") + " from ${target.name}.",
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
                MarbleDialogAction(
                    label = "Cancel",
                    tone = Aether.InkMuted,
                    onClick = { pruneFailedTarget = null }
                )
            }
        )
    }

    deleteSubscription?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteSubscription = null },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text("Delete ${target.name}?", color = Aether.Danger) },
            text = {
                Text(
                    "This removes the subscription and its " +
                        "${repo.subscriptionNodeCount(target.id)} servers.",
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
                MarbleDialogAction(
                    label = "Cancel",
                    tone = Aether.InkMuted,
                    onClick = { deleteSubscription = null }
                )
            }
        )
    }

    // ------------------------------------------------------------------- page

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = dockClearance() + 28.dp
        )
        // MARBLE_SERVERS_STACKED_GROUPS_V121 — no list-wide spacing.
        //
        // A subscription is one box: its header and every one of its servers are stacked flush
        // against each other, so the group reads as a single continuous card instead of a header
        // followed by a drift of loose per-server cards. Gaps between page sections and between
        // groups are explicit spacer items below, which is the only way a LazyColumn can space
        // *some* neighbours without spacing the rows inside a group.
    ) {
        item(key = "servers-header") {
            ServersTopBar(
                groupCount = groups.size,
                serverCount = allProfiles.size,
                addOpen = addMenuOpen,
                onAdd = { addMenuOpen = !addMenuOpen },
                onAddDismiss = { addMenuOpen = false },
                onAddAction = { action ->
                    when (action) {
                        ServersAddAction.CLIPBOARD -> {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            if (pasted.isBlank()) {
                                // Nothing to import: rather than fail silently, hand the user
                                // the sheet with the paste box already open.
                                addForm = "node"
                            } else {
                                // MARBLE_SMART_INTAKE_V122 — pasted subscription URLs become
                                // real subscriptions; config links are imported as servers.
                                val intake = libraryIntakeTarget(repo)
                                val addedId = repo.importClipboard(pasted, intake)
                                repo.selectLibrarySource(addedId ?: intake)
                            }
                        }
                        // MARBLE_QR_CAMERA_V122 — the chooser offers the live camera and the gallery.
                        ServersAddAction.QR -> qrSourceOpen = true
                        ServersAddAction.FILE -> onImportFile()
                        ServersAddAction.MANUAL_NODE -> addForm = "node"
                        ServersAddAction.MANUAL_SUBSCRIPTION -> addForm = "subscription"
                        ServersAddAction.CHAIN -> addForm = "chain"
                    }
                },
                onSort = { sortOpen = true },
                sortOpen = sortOpen,
                sortMode = settings.nodeSortMode,
                sortReverse = settings.nodeSortReverse,
                onSortPick = { mode, reverse ->
                    sortOpen = false
                    updateSettings { copy(nodeSortMode = mode, nodeSortReverse = reverse) }
                },
                onSortDismiss = { sortOpen = false }
            )
        }

        item(key = "servers-header-gap") { Spacer(Modifier.height(10.dp)) }

        item(key = "servers-search") {
            ServersSearchField(
                value = search,
                onValueChange = { search = it },
                onClear = { search = "" }
            )
        }

        item(key = "servers-search-gap") { Spacer(Modifier.height(10.dp)) }

        item(key = "servers-filters") {
            ServersFilterRail(
                repo = repo,
                groupLabel = activeGroupLabel,
                groupActive = filter.sourceId != "all",
                groupOptions = groupOptions,
                groupMenuOpen = groupMenuOpen,
                onGroupMenu = { groupMenuOpen = !groupMenuOpen },
                onGroupDismiss = { groupMenuOpen = false },
                onGroupPick = { id ->
                    groupMenuOpen = false
                    repo.selectLibrarySource(id)
                },
                protocol = filter.protocol,
                protocolTallies = protocolTallies,
                protocolMenuOpen = protocolMenuOpen,
                onProtocolMenu = { protocolMenuOpen = !protocolMenuOpen },
                onProtocolDismiss = { protocolMenuOpen = false },
                onProtocolPick = { scheme ->
                    protocolMenuOpen = false
                    updateSettings { copy(serversProtocolFilter = scheme) }
                },
                advancedOpen = advancedOpen,
                onAdvanced = { advancedOpen = !advancedOpen },
                onAdvancedDismiss = { advancedOpen = false },
                onAdvancedAction = { action ->
                    advancedOpen = false
                    when (action) {
                        ServersAdvancedAction.GROUP_BY_COUNTRY ->
                            updateSettings { copy(serversGroupByCountry = !serversGroupByCountry) }
                        ServersAdvancedAction.ONLY_REACHABLE ->
                            updateSettings { copy(serversOnlyReachable = !serversOnlyReachable) }
                        ServersAdvancedAction.RESET -> {
                            // "Reset filters" means the list shows everything again, so the scope
                            // and the search box go back too — the same set ServersFilter.cleared()
                            // describes, and the only way the active-filters badge can go quiet.
                            search = ""
                            repo.selectLibrarySource("all")
                            updateSettings {
                                copy(
                                    serversProtocolFilter = "",
                                    serversOnlyReachable = false,
                                    serversMaxPingMs = ServersQuery.MAX_PING_OFF,
                                    serversGroupByCountry = false
                                )
                            }
                        }
                        ServersAdvancedAction.REFRESH_ALL -> repo.refreshLibrarySource("all")
                        ServersAdvancedAction.RANK_ALL -> repo.smartRank()
                        ServersAdvancedAction.ALL_FILTERS -> allFiltersOpen = true
                    }
                },
                onMaxPing = { ceiling ->
                    updateSettings { copy(serversMaxPingMs = ceiling) }
                },
                onPingAll = {
                    if (filter.sourceId == "all") repo.testAll() else repo.testSource(filter.sourceId)
                }
            )
        }

        item(key = "servers-filters-gap") { Spacer(Modifier.height(10.dp)) }

        if (repo.inlineProgressActive) {
            item(key = "servers-progress") {
                ServersProbeStrip(repo = repo)
            }
            item(key = "servers-progress-gap") { Spacer(Modifier.height(10.dp)) }
        }

        groups.forEach { group ->
            val collapsed = group.key in repo.libraryCollapsedSources
            val subscription = repo.subscriptions.firstOrNull { it.id == group.key }
            val total = when (group.kind) {
                LibraryGroupKind.SUBSCRIPTION -> repo.subscriptionNodeCount(group.key)
                else -> group.profiles.size
            }

            // The header is the top of the group's box; the rows below close it.
            val stacked = !collapsed && group.profiles.isNotEmpty()

            item(key = "group-${group.key}") {
                ServersGroupHeader(
                    group = group,
                    attachedBelow = stacked,
                    subscription = subscription,
                    collapsed = collapsed,
                    shown = group.profiles.size,
                    total = total,
                    refreshing = group.key in repo.refreshingSources,
                    autoRefresh = settings.subscriptionAutoRefresh,
                    onToggle = { repo.setLibrarySourceCollapsed(group.key, !collapsed) },
                    onRefresh = { repo.refresh(group.key) },
                    onWebsite = { url -> openExternal(context, url) },
                    onMenu = {
                        when (it) {
                            ServersGroupAction.MANAGE -> {
                                manageSubscription = subscription
                                editSubscriptionName = subscription?.name.orEmpty()
                                editSubscriptionUrl = subscription?.url.orEmpty()
                            }
                            ServersGroupAction.REFRESH -> repo.refresh(group.key)
                            ServersGroupAction.COPY_URL -> {
                                clipboard.setText(AnnotatedString(subscription?.url.orEmpty()))
                                repo.setRuntimeMessage("Subscription URL copied")
                            }
                            ServersGroupAction.COPY_SERVERS -> {
                                clipboard.setText(AnnotatedString(repo.subscriptionRawText(group.key)))
                                repo.setRuntimeMessage(
                                    "${repo.subscriptionNodeCount(group.key)} server links copied"
                                )
                            }
                            ServersGroupAction.PING -> repo.testSource(group.key)
                            ServersGroupAction.SHOW_ONLY -> repo.selectLibrarySource(group.key)
                            ServersGroupAction.SHOW_ALL -> repo.selectLibrarySource("all")
                            ServersGroupAction.DELETE -> deleteSubscription = subscription
                        }
                    }
                )
            }

            if (collapsed) {
                item(key = "group-${group.key}-folded") {
                    ServersFoldedNote(count = total)
                }
            } else {
                itemsIndexed(
                    items = group.profiles,
                    key = { _, profile -> "${group.key}:${profile.id}" }
                ) { index, profile ->
                    ServersNodeCard(
                        profile = profile,
                        repo = repo,
                        result = benchmarks[profile.id],
                        active = repo.isActiveProfile(profile),
                        selected = repo.isSelectedProfile(profile),
                        lastInGroup = index == group.profiles.lastIndex,
                        probeState = repo.probeStateOf(profile.id),
                        // MARBLE_SELECT_IS_NOT_CONNECT_V121 — a tap selects. It only connects when
                        // a tunnel is already up (switching route is an explicit re-connect) or is
                        // being established, so browsing the list can never open a connection.
                        onConnect = {
                            if (repo.state == "CONNECTED" || repo.state == "CONNECTING") {
                                onConnect(profile)
                            } else {
                                repo.selectProfile(profile)
                            }
                        },
                        onEdit = {
                            renameTarget = profile
                            renameText = stripLeadingFlag(profile.name)
                        },
                        onMove = { moveTarget = profile },
                        onQr = { qrTarget = profile },
                        onDelete = { deleteTarget = profile },
                        onDetails = { onDetails(profile) }
                    )
                }
            }

            item(key = "group-${group.key}-gap") { Spacer(Modifier.height(10.dp)) }
        }
    }

    addForm?.let { form ->
        ServersAddPage(
            repo = repo,
            initialMode = form,
            onImportFile = onImportFile,
            onDismiss = { addForm = null }
        )
    }

    if (allFiltersOpen) {
        LibraryFilterSheet(
            repo = repo,
            sourceFilter = repo.librarySourceFilter,
            onSourceFilter = { repo.selectLibrarySource(it) },
            onManageSubscription = { sub ->
                manageSubscription = sub
                editSubscriptionName = sub.name
                editSubscriptionUrl = sub.url
                allFiltersOpen = false
            },
            onDismiss = { allFiltersOpen = false }
        )
    }
}

/** Every action the advanced-filter menu can run. */
private enum class ServersAdvancedAction {
    GROUP_BY_COUNTRY,
    ONLY_REACHABLE,
    RESET,
    REFRESH_ALL,
    RANK_ALL,
    ALL_FILTERS
}

/** Every action a subscription header menu can run. */
private enum class ServersGroupAction {
    MANAGE,
    REFRESH,
    COPY_URL,
    COPY_SERVERS,
    // MARBLE_ONE_PING_V121 — a subscription menu offers exactly one measurement entry. The old
    // "Rank this group" ran a second, differently-configured probe that ignored the ping method
    // chosen in Settings, so the same menu reported two different latencies for the same server.
    PING,
    SHOW_ONLY,
    SHOW_ALL,
    DELETE
}

/** One sort choice of the header menu: the mode plus the direction it implies. */
private data class ServersSortOption(
    val label: String,
    val mode: NodeSortMode,
    val reverse: Boolean
)

private val SERVERS_SORT_OPTIONS = listOf(
    ServersSortOption("Default", NodeSortMode.DEFAULT, false),
    ServersSortOption("Name", NodeSortMode.NAME, false),
    ServersSortOption("Name (Z-A)", NodeSortMode.NAME, true),
    ServersSortOption("Ping", NodeSortMode.PING, false),
    ServersSortOption("Country", NodeSortMode.COUNTRY, false),
    ServersSortOption("Protocol", NodeSortMode.PROTOCOL, false)
)

// --------------------------------------------------------------------------- top bar

/**
 * The page header: a bold headline, one quiet line of counts, and the two round controls that own
 * the page's primary verbs — add a server and change the order.
 */
@Composable
private fun ServersTopBar(
    groupCount: Int,
    serverCount: Int,
    addOpen: Boolean,
    onAdd: () -> Unit,
    onAddDismiss: () -> Unit,
    onAddAction: (ServersAddAction) -> Unit,
    onSort: () -> Unit,
    sortOpen: Boolean,
    sortMode: NodeSortMode,
    sortReverse: Boolean,
    onSortPick: (NodeSortMode, Boolean) -> Unit,
    onSortDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trx("Servers"),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    trx("$groupCount groups • $serverCount servers"),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            // MARBLE_ADD_SERVER_MENU_V121 — every way to get a server into Marble, in one menu
            // anchored to the + button instead of a sheet that had to be opened before the user
            // could even see the options.
            Box {
                ServersRoundButton(
                    icon = HomeIcon.PLUS,
                    label = "Add server",
                    selected = addOpen,
                    onClick = onAdd
                )
                ServersAddMenu(
                    expanded = addOpen,
                    onDismiss = onAddDismiss,
                    onAction = onAddAction
                )
            }
            Spacer(Modifier.width(8.dp))
            // The dropdown is anchored to the sort control's own box, so it always opens exactly
            // under the button that summoned it.
            Box {
                ServersRoundButton(
                    icon = HomeIcon.SORT,
                    label = "Sort servers",
                    selected = sortOpen,
                    onClick = onSort
                )
                DropdownMenu(
                    expanded = sortOpen,
                    onDismissRequest = onSortDismiss,
                    shape = ServersMenuShape,
                    containerColor = Aether.VoidElevated
                ) {
                    SERVERS_SORT_OPTIONS.forEach { option ->
                        val selected = sortMode == option.mode && sortReverse == option.reverse
                        ServersMenuItem(
                            label = option.label,
                            selected = selected,
                            onClick = { onSortPick(option.mode, option.reverse) }
                        )
                    }
                }
            }
        }
    }
}

/** A round, near-background control: the two verbs of the page header. */
@Composable
private fun ServersRoundButton(
    icon: HomeIcon,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val tone = if (selected) Aether.Cyan else Aether.Ink
    val description = trx(label)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Aether.GlassStrong.copy(alpha = .34f))
            .border(1.dp, if (selected) Aether.Cyan.copy(alpha = .45f) else Aether.GlassBorderSoft, CircleShape)
            .semantics { contentDescription = description }
            .kineticClickable(
                role = Role.Button,
                boundedShape = CircleShape,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        HomeVectorIcon(icon, tone, Modifier.size(19.dp))
    }
}

/**
 * MARBLE_ADD_SERVER_MENU_V121 — everything the + button can do.
 *
 * Importing is one tap from the menu (clipboard, QR image, file). Building something by hand is a
 * deliberate second choice, so "Add manually" opens its own small menu of the three things a user
 * can actually author — a single server, a subscription source, or a multi-hop chain — and only
 * then opens the form for it.
 */
private enum class ServersAddAction {
    CLIPBOARD,
    QR,
    FILE,
    MANUAL_NODE,
    MANUAL_SUBSCRIPTION,
    CHAIN
}

@Composable
private fun ServersAddMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (ServersAddAction) -> Unit
) {
    // The manual submenu replaces the first menu in the same anchor, so the two never overlap and
    // "back" is simply dismissing it.
    var manualOpen by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) manualOpen = false }

    DropdownMenu(
        expanded = expanded && !manualOpen,
        onDismissRequest = onDismiss,
        shape = ServersMenuShape,
        containerColor = Aether.VoidElevated
    ) {
        ServersMenuItem(
            label = "Import from clipboard",
            icon = HomeIcon.CLIPBOARD,
            tone = Aether.Cyan,
            detail = "Paste vless:// vmess:// ss:// trojan:// links",
            onClick = {
                onDismiss()
                onAction(ServersAddAction.CLIPBOARD)
            }
        )
        ServersMenuItem(
            label = "Import from QR code",
            icon = HomeIcon.QR,
            tone = Aether.Amethyst,
            detail = "Scan with the camera or pick a saved image",
            onClick = {
                onDismiss()
                onAction(ServersAddAction.QR)
            }
        )
        ServersMenuItem(
            label = "Import from file",
            icon = HomeIcon.SHARE,
            tone = Aether.Emerald,
            detail = "A .txt / .json / subscription export",
            onClick = {
                onDismiss()
                onAction(ServersAddAction.FILE)
            }
        )
        HorizontalDivider(color = Aether.GlassBorderSoft)
        ServersMenuItem(
            label = "Add manually",
            icon = HomeIcon.PENCIL,
            tone = Aether.Ink,
            detail = "Choose what to build",
            onClick = { manualOpen = true }
        )
        ServersMenuItem(
            label = "Chain",
            icon = HomeIcon.ROUTING,
            tone = Aether.Amber,
            detail = "Route through several servers in order",
            onClick = {
                onDismiss()
                onAction(ServersAddAction.CHAIN)
            }
        )
    }

    DropdownMenu(
        expanded = expanded && manualOpen,
        onDismissRequest = {
            manualOpen = false
            onDismiss()
        },
        shape = ServersMenuShape,
        containerColor = Aether.VoidElevated
    ) {
        ServersMenuItem(
            label = "Server",
            icon = HomeIcon.SERVER,
            tone = Aether.Cyan,
            detail = "Enter protocol, address and credentials",
            onClick = {
                manualOpen = false
                onDismiss()
                onAction(ServersAddAction.MANUAL_NODE)
            }
        )
        ServersMenuItem(
            label = "Subscription",
            icon = HomeIcon.LIBRARY,
            tone = Aether.Emerald,
            detail = "A provider URL Marble keeps up to date",
            onClick = {
                manualOpen = false
                onDismiss()
                onAction(ServersAddAction.MANUAL_SUBSCRIPTION)
            }
        )
        ServersMenuItem(
            label = "Chain",
            icon = HomeIcon.ROUTING,
            tone = Aether.Amber,
            detail = "Several hops, in order",
            onClick = {
                manualOpen = false
                onDismiss()
                onAction(ServersAddAction.CHAIN)
            }
        )
    }
}

/**
 * One row of a Marble dropdown menu: an optional glyph, the label, an optional second line of
 * detail, and a small tick on the right when the row is the chosen one.
 */
@Composable
private fun ServersMenuItem(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: HomeIcon? = null,
    tone: Color = Aether.Ink,
    detail: String = "",
    onClick: () -> Unit
) {
    DropdownMenuItem(
        enabled = enabled,
        onClick = onClick,
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (icon != null) {
                    HomeVectorIcon(icon, tone, Modifier.size(16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        trx(label),
                        color = tone,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            trx(detail),
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        trailingIcon = {
            if (selected) {
                HomeVectorIcon(HomeIcon.CHECK, Aether.Cyan, Modifier.size(15.dp))
            }
        }
    )
}

/** A switch row inside a dropdown menu; the whole row toggles. */
@Composable
private fun ServersMenuSwitch(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    DropdownMenuItem(
        onClick = onToggle,
        text = {
            Text(
                trx(label),
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        },
        trailingIcon = {
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Aether.VoidElevated,
                    checkedTrackColor = Aether.Cyan,
                    uncheckedThumbColor = Aether.VoidElevated,
                    uncheckedTrackColor = Aether.GlassStrong,
                    uncheckedBorderColor = Aether.GlassBorder
                )
            )
        }
    )
}

// --------------------------------------------------------------------------- search

/**
 * The search field: one pill, one hairline, one magnifier. The clear button appears only while
 * there is something to clear, so the field never changes width while typing.
 */
@Composable
private fun ServersSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = ServersPillShape,
        placeholder = {
            Text(
                trx("Search servers, host or protocol"),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            HomeVectorIcon(HomeIcon.SEARCH, Aether.InkMuted, Modifier.size(18.dp))
        },
        trailingIcon = {
            if (value.isNotBlank()) {
                val clearLabel = trx("Clear search")
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = clearLabel }
                        .kineticClickable(role = Role.Button, boundedShape = CircleShape, onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    HomeVectorIcon(HomeIcon.CANCEL, Aether.InkMuted, Modifier.size(13.dp))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Aether.Ink,
            unfocusedTextColor = Aether.Ink,
            focusedContainerColor = Aether.VoidElevated,
            unfocusedContainerColor = Aether.VoidElevated,
            cursorColor = Aether.Cyan,
            focusedBorderColor = Aether.Cyan.copy(alpha = .60f),
            unfocusedBorderColor = Aether.GlassBorderSoft,
            disabledBorderColor = Aether.GlassBorderSoft
        )
    )
}

// --------------------------------------------------------------------------- filter rail

/**
 * The filter rail under the search field: the active group capsule, the protocol capsule (with a
 * live count per protocol), the advanced-filter menu and the page-wide ping. Four controls, one
 * row, and each one states exactly what it is currently doing.
 */
@Composable
private fun ServersFilterRail(
    repo: AppRepository,
    groupLabel: String,
    groupActive: Boolean,
    groupOptions: List<Pair<String, String>>,
    groupMenuOpen: Boolean,
    onGroupMenu: () -> Unit,
    onGroupDismiss: () -> Unit,
    onGroupPick: (String) -> Unit,
    protocol: String,
    protocolTallies: List<ProtocolTally>,
    protocolMenuOpen: Boolean,
    onProtocolMenu: () -> Unit,
    onProtocolDismiss: () -> Unit,
    onProtocolPick: (String) -> Unit,
    advancedOpen: Boolean,
    onAdvanced: () -> Unit,
    onAdvancedDismiss: () -> Unit,
    onAdvancedAction: (ServersAdvancedAction) -> Unit,
    onMaxPing: (Int) -> Unit,
    onPingAll: () -> Unit
) {
    val settings = repo.settings
    val maxPing = settings.serversMaxPingMs
    val busy = repo.busy || repo.probeActive

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Group / source capsule.
        Box(modifier = Modifier.weight(1f, fill = false)) {
            ServersFilterCapsule(
                label = groupLabel,
                tone = if (groupActive) Aether.Cyan else Aether.InkMuted,
                icon = HomeIcon.SERVER,
                onClick = onGroupMenu,
                onClear = if (groupActive) {
                    { onGroupPick("all") }
                } else {
                    null
                }
            )
            DropdownMenu(
                expanded = groupMenuOpen,
                onDismissRequest = onGroupDismiss,
                shape = ServersMenuShape,
                containerColor = Aether.VoidElevated
            ) {
                groupOptions.forEach { (id, label) ->
                    ServersMenuItem(
                        label = label,
                        selected = repo.librarySourceFilter == id,
                        onClick = { onGroupPick(id) }
                    )
                }
            }
        }

        // Protocol capsule with per-protocol counts.
        Box {
            ServersFilterCapsule(
                label = protocol.ifBlank { "All protocols" },
                tone = if (protocol.isNotBlank()) Aether.Cyan else Aether.InkMuted,
                icon = HomeIcon.TUNNEL,
                onClick = onProtocolMenu,
                onClear = if (protocol.isNotBlank()) {
                    { onProtocolPick(ServersQuery.ALL_PROTOCOLS) }
                } else {
                    null
                }
            )
            DropdownMenu(
                expanded = protocolMenuOpen,
                onDismissRequest = onProtocolDismiss,
                shape = ServersMenuShape,
                containerColor = Aether.VoidElevated
            ) {
                ServersMenuItem(
                    label = "All protocols",
                    detail = "${protocolTallies.sumOf { it.count }} servers",
                    selected = protocol.isBlank(),
                    onClick = { onProtocolPick(ServersQuery.ALL_PROTOCOLS) }
                )
                if (protocolTallies.isNotEmpty()) HorizontalDivider(color = Aether.GlassBorderSoft)
                protocolTallies.forEach { tally ->
                    ServersMenuItem(
                        label = tally.scheme,
                        detail = "${tally.count}",
                        selected = protocol.equals(tally.scheme, ignoreCase = true),
                        onClick = { onProtocolPick(tally.scheme) }
                    )
                }
            }
        }

        // Advanced filters.
        Box {
            val advancedLabel = trx("Advanced filters")
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(ServersBadgeShape)
                    .background(
                        if (advancedOpen || settings.serversOnlyReachable || maxPing > 0 ||
                            settings.serversGroupByCountry
                        ) {
                            Aether.Cyan.copy(alpha = .12f)
                        } else {
                            Aether.GlassStrong.copy(alpha = .30f)
                        }
                    )
                    .semantics { contentDescription = advancedLabel }
                    .kineticClickable(
                        role = Role.Button,
                        boundedShape = ServersBadgeShape,
                        onClick = onAdvanced
                    ),
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(HomeIcon.FILTER, Aether.Ink, Modifier.size(17.dp))
            }
            DropdownMenu(
                expanded = advancedOpen,
                onDismissRequest = onAdvancedDismiss,
                shape = ServersMenuShape,
                containerColor = Aether.VoidElevated
            ) {
                ServersMenuSwitch(
                    label = "Group by country",
                    checked = settings.serversGroupByCountry,
                    onToggle = { onAdvancedAction(ServersAdvancedAction.GROUP_BY_COUNTRY) }
                )
                ServersMenuSwitch(
                    label = "Only reachable",
                    checked = settings.serversOnlyReachable,
                    onToggle = { onAdvancedAction(ServersAdvancedAction.ONLY_REACHABLE) }
                )
                HorizontalDivider(color = Aether.GlassBorderSoft)
                DropdownMenuItem(
                    onClick = {},
                    enabled = false,
                    text = {
                        Text(
                            trx("Max ping"),
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ServersQuery.MAX_PING_CHOICES.forEach { ceiling ->
                        val selected = maxPing == ceiling
                        val label = if (ceiling == ServersQuery.MAX_PING_OFF) {
                            "Off"
                        } else {
                            "${ceiling}ms"
                        }
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            modifier = Modifier
                                .clip(shape)
                                .background(
                                    if (selected) Aether.Cyan.copy(alpha = .14f) else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (selected) Aether.Cyan.copy(alpha = .40f) else Aether.GlassBorderSoft,
                                    shape
                                )
                                .kineticClickable(
                                    role = Role.Button,
                                    boundedShape = shape,
                                    onClick = { onMaxPing(ceiling) }
                                )
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                trx(label),
                                color = if (selected) Aether.Cyan else Aether.InkMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                HorizontalDivider(color = Aether.GlassBorderSoft)
                ServersMenuItem(
                    label = "Reset filters",
                    icon = HomeIcon.RESET,
                    tone = Aether.Amber,
                    onClick = { onAdvancedAction(ServersAdvancedAction.RESET) }
                )
                ServersMenuItem(
                    label = "Refresh all sources",
                    icon = HomeIcon.DOWNLOAD,
                    tone = Aether.Amethyst,
                    enabled = !repo.busy,
                    onClick = { onAdvancedAction(ServersAdvancedAction.REFRESH_ALL) }
                )
                ServersMenuItem(
                    label = "Rank all servers",
                    icon = HomeIcon.RANK,
                    tone = Aether.Emerald,
                    enabled = !repo.busy,
                    onClick = { onAdvancedAction(ServersAdvancedAction.RANK_ALL) }
                )
                HorizontalDivider(color = Aether.GlassBorderSoft)
                ServersMenuItem(
                    label = "All filters…",
                    icon = HomeIcon.MENU,
                    tone = Aether.Cyan,
                    onClick = { onAdvancedAction(ServersAdvancedAction.ALL_FILTERS) }
                )
            }
        }

        // Page-wide ping: measures every server of the current scope at once.
        val pingLabel = trx(
            if (groupActive) "Ping every server in this group" else "Ping every server"
        )
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(ServersBadgeShape)
                .background(if (busy) Aether.Cyan.copy(alpha = .12f) else Aether.GlassStrong.copy(alpha = .30f))
                .semantics { contentDescription = pingLabel }
                .kineticClickable(
                    enabled = !repo.busy,
                    role = Role.Button,
                    boundedShape = ServersBadgeShape,
                    onClick = onPingAll
                ),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Aether.Cyan,
                    strokeWidth = 2.dp
                )
            } else {
                HomeVectorIcon(HomeIcon.PING, Aether.Ink, Modifier.size(19.dp))
            }
        }
    }
}

/** One capsule of the filter rail: icon, live label, and an × that appears only when it filters. */
@Composable
private fun ServersFilterCapsule(
    label: String,
    tone: Color,
    icon: HomeIcon,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .widthIn(max = 190.dp)
            .clip(ServersPillShape)
            .background(tone.copy(alpha = .10f))
            .border(1.dp, tone.copy(alpha = .26f), ServersPillShape)
            .kineticClickable(role = Role.Button, boundedShape = ServersPillShape, onClick = onClick)
            .padding(start = 11.dp, end = if (onClear == null) 12.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HomeVectorIcon(icon, tone, Modifier.size(14.dp))
        Text(
            trx(label),
            color = tone,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (onClear != null) {
            val clearLabel = trx("Clear")
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = clearLabel }
                    .kineticClickable(
                        role = Role.Button,
                        boundedShape = CircleShape,
                        showIndication = false,
                        onClick = onClear
                    ),
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(HomeIcon.CANCEL, tone, Modifier.size(11.dp))
            }
        }
    }
}

/** The live probe/refresh strip: it appears only while something is actually being measured. */
@Composable
private fun ServersProbeStrip(repo: AppRepository) {
    val done = repo.probeDone.coerceAtMost(repo.probeTotal)
    val progress = if (repo.probeTotal > 0) done.toFloat() / repo.probeTotal.toFloat() else 0f
    val refreshing = repo.refreshingSources.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ServersCardShape)
            .background(Aether.VoidElevated)
            .border(1.dp, Aether.Cyan.copy(alpha = .22f), ServersCardShape)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeVectorIcon(
                if (refreshing) HomeIcon.DOWNLOAD else HomeIcon.PING,
                Aether.Cyan,
                Modifier.size(17.dp)
            )
            Text(
                if (refreshing) trx("Refreshing sources") else trx("Measuring servers"),
                color = Aether.Ink,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (repo.probeTotal > 0) {
                Text(
                    "$done/${repo.probeTotal}",
                    color = Aether.Cyan,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(ServersPillShape)
                .background(Aether.GlassStrong.copy(alpha = .40f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(ServersPillShape)
                    .background(Aether.Cyan)
            )
        }
        if (repo.probeLastName.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                val tone = when (repo.probeLastOutcome) {
                    "FAILED" -> Aether.Danger
                    "OK" -> Aether.Emerald
                    else -> Aether.Cyan
                }
                HomeVectorIcon(
                    when (repo.probeLastOutcome) {
                        "FAILED" -> HomeIcon.CANCEL
                        "OK" -> HomeIcon.PING
                        else -> HomeIcon.BENCHMARK
                    },
                    tone,
                    Modifier.size(15.dp)
                )
                Text(
                    stripLeadingFlag(repo.probeLastName),
                    modifier = Modifier.weight(1f),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (repo.probeLastOutcome) {
                        "FAILED" -> "FAILED"
                        "OK" -> "${repo.probeLastLatencyMs} ms"
                        else -> "…"
                    },
                    color = tone,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/** The quiet line that replaces a folded group's servers. */
@Composable
private fun ServersFoldedNote(count: Int) {
    Text(
        trx("$count servers hidden"),
        color = Aether.InkFaint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 6.dp)
    )
}

// --------------------------------------------------------------------------- group header

/**
 * One group header.
 *
 * Folded it is a single rounded row — chevron, name, count and the auto-update state. Open it
 * grows its own facts: the plan usage box, the expiry line, the provider website capsule, the
 * refresh control and the group menu. Nothing here resizes the servers below it.
 */
@Composable
private fun ServersGroupHeader(
    group: LibraryGroup,
    subscription: Subscription?,
    // MARBLE_SERVERS_STACKED_GROUPS_V121 — true when this header opens a box whose servers are
    // stacked flush beneath it, so it drops its bottom corners and its bottom hairline.
    attachedBelow: Boolean,
    collapsed: Boolean,
    shown: Int,
    total: Int,
    refreshing: Boolean,
    autoRefresh: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onWebsite: (String) -> Unit,
    onMenu: (ServersGroupAction) -> Unit
) {
    val accent = when (group.kind) {
        else -> Aether.Cyan
    }
    val local = subscription?.url?.isBlank() == true
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 90f else -90f,
        animationSpec = MarbleMotionSpecs.ResponseFloat,
        label = "servers-group-chevron"
    )
    val shape = if (attachedBelow) ServersGroupHeadShape else ServersCardShape

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Aether.VoidElevated)
            // An attached header keeps its side and top hairlines and lets the rows below draw the
            // rest of the box, so the group never shows a seam between its own parts.
            .serversStackedFrame(
                openBottom = attachedBelow,
                color = Aether.GlassBorderSoft
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onToggle)
                .padding(start = 11.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = .10f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .graphicsLayer { rotationZ = chevronRotation }
                        .size(14.dp)
                ) {
                    HomeVectorIcon(HomeIcon.CHEVRON, accent, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (group.flag.isNotBlank()) {
                        Text(group.flag, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    Text(
                        group.title,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    (if (total == 1) "1 server" else "$total servers") +
                        if (shown != total) " • $shown shown" else "",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (subscription != null) {
                val refreshLabel = trx("Refresh ${group.title}")
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .semantics { contentDescription = refreshLabel }
                        .kineticClickable(
                            enabled = !local,
                            role = Role.Button,
                            boundedShape = RoundedCornerShape(11.dp),
                            onClick = onRefresh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = Aether.Amethyst,
                            strokeWidth = 2.dp
                        )
                    } else {
                        HomeVectorIcon(
                            HomeIcon.RESET,
                            if (local) Aether.InkFaint else Aether.Amethyst,
                            Modifier.size(16.dp)
                        )
                    }
                }
            }
            ServersGroupMenuButton(
                group = group,
                subscription = subscription,
                autoRefresh = autoRefresh,
                onMenu = onMenu
            )
        }

        AnimatedVisibility(
            visible = !collapsed && subscription != null,
            enter = fadeIn(MarbleMotionSpecs.ResponseFloat) + expandVertically(MarbleMotionSpecs.Layout),
            exit = fadeOut(MarbleMotionSpecs.ExitFloat) + shrinkVertically(MarbleMotionSpecs.Layout)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 11.dp, end = 11.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (subscription != null && !local) {
                    // Plan accounting, exactly as the provider reported it.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Aether.Cyan.copy(alpha = .10f))
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeVectorIcon(HomeIcon.INFO, Aether.Cyan, Modifier.size(15.dp))
                        Text(
                            subscriptionUsageText(subscription),
                            color = Aether.Cyan,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    Text(
                        "${trx("Expires")}: ${subscriptionExpiryText(subscription)}",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (subscription != null && subscription.url.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 32.dp)
                                .clip(ServersPillShape)
                                .border(1.dp, Aether.GlassBorderSoft, ServersPillShape)
                                .kineticClickable(
                                    role = Role.Button,
                                    boundedShape = ServersPillShape,
                                    onClick = { onWebsite(subscription.url) }
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HomeVectorIcon(HomeIcon.GLOBE, Aether.Ink, Modifier.size(13.dp))
                            Text(
                                trx("Website"),
                                color = Aether.Ink,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        when {
                            subscription == null -> trx("Local engine")
                            local -> trx("Local source")
                            autoRefresh -> trx("Auto-update on")
                            else -> trx("Auto-update off")
                        },
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The three-dot control of one group header, with its menu anchored underneath. */
@Composable
private fun ServersGroupMenuButton(
    group: LibraryGroup,
    subscription: Subscription?,
    autoRefresh: Boolean,
    onMenu: (ServersGroupAction) -> Unit
) {
    var open by remember(group.key) { mutableStateOf(false) }
    val menuLabel = trx("Group actions")
    Box {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .semantics { contentDescription = menuLabel }
                .kineticClickable(
                    role = Role.Button,
                    boundedShape = RoundedCornerShape(11.dp),
                    onClick = { open = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            HomeVectorIcon(HomeIcon.MORE, if (open) Aether.Cyan else Aether.InkMuted, Modifier.size(16.dp))
        }
        ServersGroupMenu(
            group = group,
            subscription = subscription,
            autoRefresh = autoRefresh,
            expanded = open,
            onDismiss = { open = false },
            onMenu = onMenu
        )
    }
}

/** The three-dot menu of one group header. */
@Composable
private fun ServersGroupMenu(
    group: LibraryGroup,
    subscription: Subscription?,
    autoRefresh: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMenu: (ServersGroupAction) -> Unit
) {
    val open = expanded
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = ServersMenuShape,
        containerColor = Aether.VoidElevated
    ) {
        if (subscription != null) {
            ServersMenuItem(
                label = "Manage subscription",
                icon = HomeIcon.MODE,
                tone = Aether.Cyan,
                detail = if (subscription.url.isBlank()) {
                    "Local source"
                } else {
                    "Updated ${relativeTime(subscription.updatedAt)}"
                },
                onClick = {
                    onDismiss()
                    onMenu(ServersGroupAction.MANAGE)
                }
            )
            ServersMenuItem(
                label = "Refresh",
                icon = HomeIcon.RESET,
                tone = Aether.Amethyst,
                detail = if (autoRefresh) "Auto-update is on" else "Auto-update is off",
                enabled = subscription.url.isNotBlank(),
                onClick = {
                    onDismiss()
                    onMenu(ServersGroupAction.REFRESH)
                }
            )
            HorizontalDivider(color = Aether.GlassBorderSoft)
            ServersMenuItem(
                label = "Copy subscription URL",
                icon = HomeIcon.SHARE,
                tone = Aether.Ink,
                enabled = subscription.url.isNotBlank(),
                onClick = {
                    onDismiss()
                    onMenu(ServersGroupAction.COPY_URL)
                }
            )
            ServersMenuItem(
                label = "Copy all servers",
                icon = HomeIcon.CLIPBOARD,
                tone = Aether.Ink,
                enabled = group.profiles.isNotEmpty(),
                onClick = {
                    onDismiss()
                    onMenu(ServersGroupAction.COPY_SERVERS)
                }
            )
        }
        ServersMenuItem(
            label = "Ping this group",
            icon = HomeIcon.PING,
            tone = Aether.Emerald,
            onClick = {
                onDismiss()
                onMenu(ServersGroupAction.PING)
            }
        )
        HorizontalDivider(color = Aether.GlassBorderSoft)
        ServersMenuItem(
            label = "Show only this group",
            icon = HomeIcon.FILTER,
            tone = Aether.Ink,
            onClick = {
                onDismiss()
                onMenu(ServersGroupAction.SHOW_ONLY)
            }
        )
        ServersMenuItem(
            label = "Show all groups",
            icon = HomeIcon.SERVER,
            tone = Aether.Ink,
            onClick = {
                onDismiss()
                onMenu(ServersGroupAction.SHOW_ALL)
            }
        )
        if (subscription != null) {
            HorizontalDivider(color = Aether.GlassBorderSoft)
            ServersMenuItem(
                label = "Delete source",
                icon = HomeIcon.TRASH,
                tone = Aether.Danger,
                onClick = {
                    onDismiss()
                    onMenu(ServersGroupAction.DELETE)
                }
            )
        }
    }
}

// --------------------------------------------------------------------------- server card

/** The tone of a measured latency, in the active theme's own palette. */
@Composable
private fun serverPingTone(latencyMs: Int, measured: Boolean, testing: Boolean): Color = when {
    testing -> Aether.Cyan
    !measured -> Aether.Danger
    latencyMs < 100 -> Aether.Emerald
    latencyMs <= 250 -> Aether.Amber
    else -> Aether.Danger
}

/** The wire-scheme accent of a node, keyed by protocol. */
@Composable
private fun protocolTone(scheme: String): Color = when (scheme.trim().uppercase()) {
    "VLESS" -> Aether.Amethyst
    "VMESS" -> Aether.Cyan
    "TROJAN" -> Aether.Emerald
    "SHADOWSOCKS", "SS" -> Aether.Amber
    "HYSTERIA2", "HY2" -> Aether.SlateBright
    "WIREGUARD" -> Aether.Slate
    "SSH" -> Aether.InkMuted
    else -> Aether.Cyan
}

/**
 * One server, one row of its subscription's box.
 *
 * Anatomy, left to right: the state bar, the country, the identity column (bold name above a
 * protocol badge and the endpoint), the latency capsule and the row's own menu. Swiping right
 * still opens the rename dialog for people who liked that shortcut.
 *
 * MARBLE_SERVERS_STACKED_GROUPS_V121 — the row has no card of its own. It stacks flush under the
 * subscription header, shares the group's outline, and is separated from its neighbours by a
 * hairline only, so a subscription reads as one box of servers.
 *
 * MARBLE_SELECT_IS_NOT_CONNECT_V121 — three distinct row states, none of which change geometry:
 * connected (emerald bar + "Connected"), selected (cyan bar + "Selected", the server the connect
 * button will use) and plain. Tapping a row selects it; see the call site for the one case where
 * a tap re-connects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServersNodeCard(
    profile: ProxyProfile,
    repo: AppRepository,
    result: BenchmarkResult?,
    active: Boolean,
    selected: Boolean,
    lastInGroup: Boolean,
    probeState: ProbeState,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit
) {
    val measured = result?.takeIf { it.success > 0 && it.latencyMs >= 20 }
    val latency = measured?.latencyMs?.toInt() ?: 0
    val testing = probeState == ProbeState.TESTING
    val securing = !active && repo.state == "CONNECTING" && repo.stateDetail == profile.name
    val routeTone = if (active) Aether.Emerald else Aether.Cyan
    // The row states, in priority order. Only colour and one word ever change.
    val stateTone = when {
        active -> Aether.Emerald
        securing -> Aether.Amethyst
        selected -> Aether.Cyan
        else -> Color.Transparent
    }
    val rowShape = if (lastInGroup) ServersGroupTailShape else ServersGroupBodyShape
    val rowFill by animateColorAsState(
        targetValue = when {
            active -> Aether.Emerald.copy(alpha = .09f)
            securing -> Aether.Amethyst.copy(alpha = .09f)
            selected -> Aether.Cyan.copy(alpha = .07f)
            else -> Color.Transparent
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "servers-row-state"
    )
    val country = ServersQuery.countryOf(profile)
    val name = stripLeadingFlag(profile.name)
    val flag = leadingFlagGlyph(profile.name) ?: country.flag
    val clipboard = LocalClipboardManager.current

    val swipeState = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipeState.settledValue) {
        when (swipeState.settledValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onEdit()
                swipeState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> swipeState.reset()
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = !repo.busy,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val tone = Aether.Amethyst
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(rowShape)
                    .background(tone.copy(alpha = .12f))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeVectorIcon(HomeIcon.PENCIL, tone, Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    trx("Edit"),
                    color = tone,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(Aether.VoidElevated.copy(alpha = .98f))
                // Keep every server surface opaque and self-contained: state tint sits inside
                // the rounded row instead of leaking into the list behind it.
                .background(Color.White.copy(alpha = if (homeCloudDark()) .025f else .88f))
                .background(rowFill)
                // The group's own outline continues through this row; the top edge is a hairline
                // separator drawn by the frame's neighbour, never a second frame.
                .serversStackedFrame(
                    openTop = true,
                    openBottom = !lastInGroup,
                    color = Aether.GlassBorderSoft
                )
                .kineticClickable(
                    enabled = !repo.busy && !active,
                    role = Role.Button,
                    boundedShape = rowShape,
                    onClick = onConnect
                )
                .padding(start = 12.dp, end = 5.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // State bar: the whole vocabulary of "which server is this?" in three pixels.
            Box(
                Modifier
                    .width(3.dp)
                    .height(26.dp)
                    .clip(ServersPillShape)
                    .background(stateTone)
            )
            Spacer(Modifier.width(9.dp))
            // Country: the flag the label carried, else the resolved one, else the scheme initial.
            Text(
                flag.takeIf { it.isNotBlank() && it != ServerCountry.UNKNOWN.flag }
                    ?: profile.scheme.trim().take(1).uppercase().ifBlank { "S" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.width(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        name,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
                    )
                    if (active) {
                        Text(
                            trx("Connected"),
                            color = routeTone,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(ServersBadgeShape)
                                .background(routeTone.copy(alpha = .13f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    } else if (securing) {
                        Text(
                            trx("Securing"),
                            color = Aether.Amethyst,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    } else if (selected) {
                        Text(
                            trx("Selected"),
                            color = Aether.Cyan,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(ServersBadgeShape)
                                .background(Aether.Cyan.copy(alpha = .12f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tone = protocolTone(profile.scheme)
                    Text(
                        ServersQuery.badge(profile),
                        color = tone,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(ServersBadgeShape)
                            .background(tone.copy(alpha = .12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        ServersQuery.address(profile),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            ServersPingCapsule(
                latencyMs = latency,
                measured = measured != null,
                testing = testing,
                // "It failed" and "it was never tried" are different facts and must look different.
                attempted = result != null && measured == null
            )
            // MARBLE_PING_SPACING_V130 — breathing room between the latency capsule and the
            // three-dot menu so they never touch. The capsule is now slightly smaller too.
            Spacer(Modifier.width(8.dp))
            ServersNodeMenu(
                profile = profile,
                repo = repo,
                onEdit = onEdit,
                onMove = onMove,
                onQr = onQr,
                onDelete = onDelete,
                onDetails = onDetails,
                onCopyLink = {
                    clipboard.setText(
                        AnnotatedString(profile.raw.trim().ifBlank { profile.configJson })
                    )
                    repo.setRuntimeMessage("Config copied")
                },
                onCopyJson = {
                    clipboard.setText(AnnotatedString(profile.configJson))
                    repo.setRuntimeMessage("Xray JSON copied")
                }
            )
        }
    }
}

/**
 * The latency capsule. It never changes size.
 *
 * MARBLE_NO_PHANTOM_PING_V121 — a server that has never been measured reads "—" in a quiet tone,
 * not "0 ms" in red. A freshly imported subscription has no measurements at all, and printing a
 * zero next to every one of its servers claimed both a measurement and an impossible latency.
 * Red is reserved for a probe that actually ran and actually failed.
 */
@Composable
private fun ServersPingCapsule(
    latencyMs: Int,
    measured: Boolean,
    testing: Boolean,
    attempted: Boolean = false
) {
    val tone = when {
        testing -> Aether.Cyan
        measured -> serverPingTone(latencyMs, true, false)
        // A probe that ran and failed is a real, red fact; one that never ran is simply unknown.
        attempted -> Aether.Danger
        else -> Aether.InkFaint
    }
    val spoken = when {
        testing -> trx("Testing server")
        measured -> trx("Latency") + " $latencyMs ms, " + libraryPingQuality(latencyMs)
        attempted -> trx("No response")
        else -> trx("Not measured")
    }
    Box(
        modifier = Modifier
            // MARBLE_PING_WIFI_V122 — the capsule carries its own wifi glyph, so it needs a
            // touch more room; it still never crowds the three-dot menu.
            .widthIn(min = 64.dp)
            .height(28.dp)
            .clip(ServersPillShape)
            .background(tone.copy(alpha = .12f))
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center
    ) {
        when {
            testing -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = tone,
                strokeWidth = 1.6.dp
            )

            !measured -> Text(
                if (attempted) "✕" else "—",
                color = tone,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )

            else -> Row(
                modifier = Modifier.padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // MARBLE_PING_WIFI_V122 — the latency reads as a measurement at a glance.
                HomeVectorIcon(HomeIcon.PING, tone, Modifier.size(13.dp))
                Text(
                    "$latencyMs",
                    color = tone,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1
                )
                Text(
                    trx("ms"),
                    color = tone.copy(alpha = .72f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/** The three-dot menu of one server card. */
@Composable
private fun ServersNodeMenu(
    profile: ProxyProfile,
    repo: AppRepository,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyJson: () -> Unit
) {
    var open by remember(profile.id, profile.subscriptionId) { mutableStateOf(false) }
    var jsonOpen by remember(profile.id) { mutableStateOf(false) }
    var jsonText by remember(profile.id, profile.configJson) {
        mutableStateOf(profile.configJson)
    }
    val canEditJson = !profile.scheme.equals("ssh", true)

    if (jsonOpen) {
        AlertDialog(
            onDismissRequest = { jsonOpen = false },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = {
                Column {
                    Text(trx("Edit Xray JSON"), color = Aether.Ink)
                    Text(
                        stripLeadingFlag(profile.name),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.S)) {
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 420.dp),
                        colors = marbleOutlinedTextFieldColors(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        label = { Text(trx("Effective config JSON")) },
                        minLines = 10,
                        maxLines = 22
                    )
                    Text(
                        if (profile.subscriptionId == "manual") {
                            trx("Manual server • edits are stored locally.")
                        } else {
                            trx("Subscription server • a refresh can replace this edit.")
                        },
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                MarbleDialogAction(
                    label = "Save JSON",
                    tone = Aether.Cyan,
                    variant = PrismButtonVariant.Primary,
                    onClick = {
                        if (repo.updateProfileJson(profile.id, jsonText, profile.subscriptionId)) {
                            jsonOpen = false
                        }
                    }
                )
            },
            dismissButton = {
                MarbleDialogAction(
                    label = "Cancel",
                    tone = Aether.InkMuted,
                    onClick = { jsonOpen = false }
                )
            }
        )
    }

    Box {
        PrismIconButton(
            onClick = { open = true },
            tone = if (open) Aether.Cyan else Aether.InkMuted,
            selected = open,
            enabled = !repo.busy,
            size = 34.dp,
            descriptiveLabel = "More actions for ${stripLeadingFlag(profile.name)}"
        ) {
            HomeVectorIcon(
                HomeIcon.MORE,
                if (open) Aether.Cyan else Aether.InkMuted,
                Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = ServersMenuShape,
            containerColor = Aether.VoidElevated
        ) {
            ServersMenuItem(
                label = "Edit",
                icon = HomeIcon.PENCIL,
                tone = Aether.Ink,
                onClick = {
                    open = false
                    onEdit()
                }
            )
            ServersMenuItem(
                label = "Copy link",
                icon = HomeIcon.SHARE,
                tone = Aether.Ink,
                onClick = {
                    open = false
                    onCopyLink()
                }
            )
            ServersMenuItem(
                label = "Export QR code",
                icon = HomeIcon.QR,
                tone = Aether.Ink,
                onClick = {
                    open = false
                    onQr()
                }
            )
            ServersMenuItem(
                // MARBLE_PROBE_TOOLKIT_V130 — the menu says which measurement will actually run:
                // Smart / Real test / TCP ping / ICMP ping / HTTP ping / DNS ping are one
                // Settings choice, so the ⋯ action can never be a silent surprise.
                label = "${trx("Ping")} • ${trx(probeMethodTitle(repo.settings.probeMethod))}",
                icon = HomeIcon.PING,
                tone = Aether.Cyan,
                enabled = !repo.busy,
                onClick = {
                    open = false
                    repo.fullTest(profile)
                }
            )
            ServersMenuItem(
                label = "Move to group",
                icon = HomeIcon.FOLDER,
                tone = Aether.Ink,
                enabled = !repo.busy,
                onClick = {
                    open = false
                    onMove()
                }
            )
            HorizontalDivider(color = Aether.GlassBorderSoft)
            ServersMenuItem(
                label = "Details",
                icon = HomeIcon.DETAILS,
                tone = Aether.InkMuted,
                onClick = {
                    open = false
                    onDetails()
                }
            )
            ServersMenuItem(
                label = "Copy Xray JSON",
                icon = HomeIcon.CLIPBOARD,
                tone = Aether.InkMuted,
                onClick = {
                    open = false
                    onCopyJson()
                }
            )
            if (canEditJson) {
                ServersMenuItem(
                    label = "Edit Xray JSON",
                    icon = HomeIcon.MODE,
                    tone = Aether.InkMuted,
                    onClick = {
                        open = false
                        jsonText = profile.configJson
                        jsonOpen = true
                    }
                )
            }
            ServersMenuItem(
                label = "Duplicate to Manual",
                icon = HomeIcon.LIBRARY,
                tone = Aether.InkMuted,
                enabled = !repo.busy,
                onClick = {
                    open = false
                    repo.duplicateProfile(profile.id, profile.subscriptionId)
                }
            )
            HorizontalDivider(color = Aether.GlassBorderSoft)
            ServersMenuItem(
                label = "Delete",
                icon = HomeIcon.TRASH,
                tone = Aether.Danger,
                enabled = !repo.busy,
                onClick = {
                    open = false
                    onDelete()
                }
            )
        }
    }
}

// --------------------------------------------------------------------------- server dialogs

/** "Move to group": the server keeps its identity, only its owner changes. */
@Composable
private fun ServersMoveDialog(
    profile: ProxyProfile,
    repo: AppRepository,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // MARBLE_MANUAL_BUCKET_V122 — Manual is always a valid destination.
    val targets: List<Pair<String, String>> = buildList {
        add("manual" to "Manual")
        repo.subscriptions.forEach { add(it.id to it.name) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        shape = ServersCardShape,
        title = {
            Column {
                Text(trx("Move to group"), color = Aether.Ink)
                Text(
                    stripLeadingFlag(profile.name),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (targets.isEmpty()) {
                    Text(
                        trx("No other group can hold this server yet."),
                        color = Aether.Amber,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                targets.forEach { (id, label) ->
                    val current = profile.subscriptionId == id
                    PrismSelectionTile(
                        label = label,
                        selected = current,
                        tone = Aether.Cyan,
                        modifier = Modifier.fillMaxWidth(),
                        detail = if (current) trx("Current group") else "",
                        minHeight = 44.dp,
                        alignment = Alignment.CenterStart,
                        enabled = !current,
                        onClick = { onPick(id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            MarbleDialogAction(label = "Cancel", tone = Aether.InkMuted, onClick = onDismiss)
        }
    )
}

/**
 * "Export QR code": the config link rendered as a real QR symbol, on device.
 *
 * The symbol itself is deliberately pure black on white — a scanner needs maximum contrast, so
 * this is the one surface that does not follow the theme. Everything around it does.
 */
@Composable
private fun ServersQrDialog(
    profile: ProxyProfile,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val payload = profile.raw.trim().ifBlank { profile.configJson }
    val symbol = remember(payload) {
        runCatching { QrCode.encode(payload, QrEcc.M) }
            .recoverCatching { QrCode.encode(payload, QrEcc.L) }
            .getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        shape = ServersCardShape,
        title = {
            Column {
                Text(trx("Export QR code"), color = Aether.Ink)
                Text(
                    stripLeadingFlag(profile.name),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (symbol == null) {
                    Text(
                        trx("This config is too long to fit in a QR code."),
                        color = Aether.Amber,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                    ) {
                        val quiet = 4
                        val total = symbol.size + quiet * 2
                        val cell = size.minDimension / total
                        for (row in 0 until symbol.size) {
                            for (column in 0 until symbol.size) {
                                if (!symbol.isDark(row, column)) continue
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset((column + quiet) * cell, (row + quiet) * cell),
                                    size = Size(cell + .5f, cell + .5f)
                                )
                            }
                        }
                    }
                    Text(
                        trx("Point another phone at the code to import this server."),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            MarbleDialogAction(
                label = "Copy link",
                tone = Aether.Cyan,
                variant = PrismButtonVariant.Primary,
                onClick = {
                    onShare(payload)
                    onDismiss()
                }
            )
        },
        dismissButton = {
            MarbleDialogAction(label = "Close", tone = Aether.InkMuted, onClick = onDismiss)
        }
    )
}

/** Manage one subscription: identity, links, failed-server cleanup and deletion. */
@Composable
private fun ServersSubscriptionDialog(
    subscription: Subscription,
    repo: AppRepository,
    nameDraft: String,
    urlDraft: String,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onPrune: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val failedPingCount = repo.failedSubscriptionNodeCount(subscription.id, "TCP")
    val failedTunnelCount = repo.failedSubscriptionNodeCount(subscription.id, "TUNNEL")
    val disconnected = repo.state == "DISCONNECTED"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        shape = ServersCardShape,
        title = {
            Column {
                Text(trx("Manage subscription"), color = Aether.Ink)
                Text(
                    "${repo.subscriptionNodeCount(subscription.id)} servers • " +
                        relativeTime(subscription.updatedAt),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = onNameChange,
                    label = { Text(trx("Source name")) },
                    singleLine = true,
                    shape = ServersCardShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = marbleOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = onUrlChange,
                    label = { Text(trx("Subscription URL")) },
                    singleLine = true,
                    shape = ServersCardShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = marbleOutlinedTextFieldColors()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CyberButton(
                        label = if (subscription.url.isBlank()) "Local source" else "Copy URL",
                        color = Aether.Emerald,
                        modifier = Modifier.weight(1f),
                        enabled = subscription.url.isNotBlank()
                    ) {
                        clipboard.setText(AnnotatedString(subscription.url))
                        repo.setRuntimeMessage("Subscription URL copied")
                    }
                    CyberButton(
                        label = "Copy servers",
                        color = Aether.Cyan,
                        modifier = Modifier.weight(1f),
                        enabled = repo.subscriptionNodeCount(subscription.id) > 0
                    ) {
                        clipboard.setText(AnnotatedString(repo.subscriptionRawText(subscription.id)))
                        repo.setRuntimeMessage(
                            "${repo.subscriptionNodeCount(subscription.id)} server links copied"
                        )
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
                    enabled = !repo.busy && failedPingCount > 0 && disconnected
                ) { onPrune("TCP") }
                CyberButton(
                    label = "Remove failed tunnel ($failedTunnelCount)",
                    color = Aether.Danger,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !repo.busy && failedTunnelCount > 0 && disconnected
                ) { onPrune("TUNNEL") }
                Text(
                    trx("Only servers with a stored failed result of that exact test type are removed."),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CyberButton(
                        label = "View servers",
                        color = Aether.Cyan,
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.selectLibrarySource(subscription.id)
                        onDismiss()
                    }
                    CyberButton(
                        label = if (subscription.url.isBlank()) "Local" else "Refresh",
                        color = Aether.Amethyst,
                        modifier = Modifier.weight(1f),
                        enabled = !repo.busy && subscription.url.isNotBlank()
                    ) {
                        onDismiss()
                        repo.refresh(subscription.id)
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
                    if (repo.updateSubscription(subscription.id, nameDraft, urlDraft)) onDismiss()
                }
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleDialogAction(
                    label = "Delete",
                    tone = Aether.Danger,
                    enabled = !repo.busy,
                    onClick = onDelete
                )
                MarbleDialogAction(label = "Close", tone = Aether.InkMuted, onClick = onDismiss)
            }
        }
    )
}

// --------------------------------------------------------------------------- add node

/** One row of a dropdown field: its label plus an optional reason it cannot be chosen. */
private data class ServersOption(
    val value: String,
    val label: String = value,
    val enabled: Boolean = true,
    val detail: String? = null
)

/**
 * A label-outlined dropdown that opens a fully rounded Material 3 menu.
 *
 * It is a real [OutlinedTextField] so its label floats and its metrics match the text fields next
 * to it; the read-only surface simply forwards taps to the menu.
 */
@Composable
private fun ServersDropdownField(
    label: String,
    value: String,
    options: List<ServersOption>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember(label, value) { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.value == value }?.label ?: value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(trx(label)) },
            shape = ServersCardShape,
            colors = marbleOutlinedTextFieldColors(),
            trailingIcon = {
                Box(
                    Modifier
                        .graphicsLayer { rotationZ = if (open) 180f else 0f }
                        .size(15.dp)
                ) {
                    HomeVectorIcon(HomeIcon.CHEVRON, Aether.InkFaint, Modifier.fillMaxSize())
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = options.any { it.enabled }) { open = true }
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = ServersMenuShape,
            containerColor = Aether.VoidElevated
        ) {
            options.forEach { option ->
                val selected = option.value == value
                DropdownMenuItem(
                    enabled = option.enabled,
                    onClick = {
                        onPick(option.value)
                        open = false
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                option.label,
                                color = if (option.enabled) Aether.Ink else Aether.InkFaint,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                            if (option.detail != null) {
                                Text(
                                    option.detail,
                                    color = Aether.Amber,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (selected) {
                            HomeVectorIcon(HomeIcon.ACTIVE, Aether.Cyan, Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

/** A text field whose label sits on the outline, used for both text and numeric input. */
@Composable
private fun ServersField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true
) {
    val hint: (@Composable () -> Unit)? = if (placeholder.isBlank()) {
        null
    } else {
        { Text(placeholder) }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(trx(label)) },
        placeholder = hint,
        singleLine = singleLine,
        isError = isError,
        shape = ServersCardShape,
        colors = marbleOutlinedTextFieldColors(),
        trailingIcon = trailing
    )
}

/**
 * The Add-node form.
 *
 * Every transport the engine can actually build is here, and the ones it cannot are still listed —
 * greyed out with the reason — so the list never pretends to offer something that would fail at
 * connect time. The Save button's disabled state comes from the exact same validation the builder
 * uses, and its reason is shown under the form.
 */
@Composable
private fun ServersAddNodeForm(
    repo: AppRepository,
    target: String,
    onTargetChange: (String) -> Unit,
    onSave: (ManualConfigDraft, String) -> Unit
) {
    var draft by remember { mutableStateOf(ManualConfigDraft()) }
    var showSecret by remember { mutableStateOf(false) }

    val protocol = draft.protocol
    val stream = protocol == ManualProtocol.VLESS || protocol == ManualProtocol.VMESS ||
        protocol == ManualProtocol.TROJAN
    val realityCapable = draft.transport in setOf("raw", "xhttp", "grpc")
    val missing = ManualConfigBuilder.missingRequirement(draft)
    // MARBLE_MANUAL_BUCKET_V122 — Manual is always a valid destination.
    val targetOptions = buildList {
        add("manual" to "Manual")
        repo.subscriptions.forEach { add(it.id to it.name) }
    }
    val targetName = targetOptions.firstOrNull { it.first == target }?.second

    fun update(block: (ManualConfigDraft) -> ManualConfigDraft) {
        draft = block(draft)
    }

    Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.M)) {
        ServersField(
            label = "Name",
            value = draft.name,
            onValueChange = { name -> update { it.copy(name = name) } },
            placeholder = "Germany · Frankfurt · 03",
            modifier = Modifier.fillMaxWidth()
        )

        ServersDropdownField(
            label = "Protocol",
            value = protocol.label,
            options = ADD_NODE_PROTOCOLS.map {
                ServersOption(
                    value = it.label,
                    enabled = it.target != null,
                    detail = it.note.ifBlank { null }
                )
            },
            onPick = { label ->
                val picked = ADD_NODE_PROTOCOLS.firstOrNull { it.label == label }?.target ?: return@ServersDropdownField
                draft = ManualConfigDraft(protocol = picked).copy(
                    name = draft.name,
                    host = draft.host,
                    port = when (picked) {
                        ManualProtocol.HTTP -> "80"
                        ManualProtocol.SOCKS5 -> "1080"
                        ManualProtocol.SSH -> "22"
                        ManualProtocol.WIREGUARD -> "51820"
                        else -> "443"
                    },
                    security = when (picked) {
                        ManualProtocol.SHADOWSOCKS,
                        ManualProtocol.HTTP,
                        ManualProtocol.SOCKS5,
                        ManualProtocol.SSH,
                        ManualProtocol.WIREGUARD -> "none"
                        else -> "tls"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ServersField(
                label = "Server",
                value = draft.host,
                onValueChange = { host -> update { it.copy(host = host) } },
                placeholder = "1.2.3.4 or host.example",
                modifier = Modifier.weight(1.6f)
            )
            ServersField(
                label = "Port",
                value = draft.port,
                onValueChange = { text -> update { it.copy(port = text.filter(Char::isDigit).take(5)) } },
                isError = draft.port.trim().toIntOrNull()?.let { it !in 1..65535 } == true,
                modifier = Modifier.weight(.8f)
            )
        }

        when (protocol) {
            ManualProtocol.XRAY_JSON -> OutlinedTextField(
                value = draft.customJson,
                onValueChange = { json -> update { it.copy(customJson = json) } },
                label = { Text(trx("Xray config / outbound JSON")) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 8,
                maxLines = 20,
                shape = ServersCardShape,
                colors = marbleOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            ManualProtocol.VLESS -> {
                ServersSecretField(
                    label = "UUID",
                    value = draft.uuid,
                    onValueChange = { uuid -> update { it.copy(uuid = uuid) } },
                    visible = showSecret,
                    onToggle = { showSecret = !showSecret }
                )
                ServersField(
                    label = "Encryption",
                    value = draft.encryption,
                    onValueChange = { encryption -> update { it.copy(encryption = encryption) } },
                    placeholder = "none",
                    modifier = Modifier.fillMaxWidth()
                )
                ServersDropdownField(
                    label = "Flow",
                    value = draft.flow,
                    options = SERVERS_FLOWS.map {
                        ServersOption(value = it, label = it.ifBlank { "none" })
                    },
                    onPick = { flow -> update { it.copy(flow = flow) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ManualProtocol.VMESS -> {
                ServersSecretField(
                    label = "UUID",
                    value = draft.uuid,
                    onValueChange = { uuid -> update { it.copy(uuid = uuid) } },
                    visible = showSecret,
                    onToggle = { showSecret = !showSecret }
                )
                ServersField(
                    label = "Cipher",
                    value = draft.encryption,
                    onValueChange = { cipher -> update { it.copy(encryption = cipher) } },
                    placeholder = "auto",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ManualProtocol.TROJAN -> ServersSecretField(
                label = "Password",
                value = draft.password,
                onValueChange = { password -> update { it.copy(password = password) } },
                visible = showSecret,
                onToggle = { showSecret = !showSecret }
            )

            ManualProtocol.SHADOWSOCKS -> {
                ServersDropdownField(
                    label = "Method",
                    value = draft.method,
                    options = SERVERS_SS_METHODS.map { ServersOption(it) },
                    onPick = { method -> update { it.copy(method = method) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersSecretField(
                    label = "Password",
                    value = draft.password,
                    onValueChange = { password -> update { it.copy(password = password) } },
                    visible = showSecret,
                    onToggle = { showSecret = !showSecret }
                )
            }

            ManualProtocol.HYSTERIA2 -> {
                ServersSecretField(
                    label = "Auth password",
                    value = draft.password,
                    onValueChange = { password -> update { it.copy(password = password) } },
                    visible = showSecret,
                    onToggle = { showSecret = !showSecret }
                )
                ServersField(
                    label = "SNI",
                    value = draft.sni,
                    onValueChange = { sni -> update { it.copy(sni = sni) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersDropdownField(
                    label = "Fingerprint",
                    value = draft.fingerprint,
                    options = SERVERS_FINGERPRINTS.map { ServersOption(it) },
                    onPick = { fingerprint -> update { it.copy(fingerprint = fingerprint) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "ALPN",
                    value = draft.alpn,
                    onValueChange = { alpn -> update { it.copy(alpn = alpn) } },
                    placeholder = "h3,h2,http/1.1",
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "Cipher suites (: separated)",
                    value = draft.cipherSuites,
                    onValueChange = { suites -> update { it.copy(cipherSuites = suites) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersInsecureSwitch(draft.allowInsecure) {
                    update { it.copy(allowInsecure = !it.allowInsecure) }
                }
            }

            ManualProtocol.HTTP, ManualProtocol.HTTPS, ManualProtocol.SOCKS5 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServersField(
                        label = "Username",
                        value = draft.username,
                        onValueChange = { username -> update { it.copy(username = username) } },
                        modifier = Modifier.weight(1f)
                    )
                    ServersField(
                        label = "Password",
                        value = draft.password,
                        onValueChange = { password -> update { it.copy(password = password) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (protocol == ManualProtocol.HTTPS) {
                    ServersField(
                        label = "TLS server name",
                        value = draft.sni,
                        onValueChange = { sni -> update { it.copy(sni = sni) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ServersDropdownField(
                        label = "Fingerprint",
                        value = draft.fingerprint,
                        options = SERVERS_FINGERPRINTS.map { ServersOption(it) },
                        onPick = { fingerprint -> update { it.copy(fingerprint = fingerprint) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ServersField(
                        label = "Cipher suites (: separated)",
                        value = draft.cipherSuites,
                        onValueChange = { suites -> update { it.copy(cipherSuites = suites) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ServersInsecureSwitch(draft.allowInsecure) {
                        update { it.copy(allowInsecure = !it.allowInsecure) }
                    }
                }
            }

            ManualProtocol.SSH -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServersField(
                        label = "Username",
                        value = draft.username,
                        onValueChange = { username -> update { it.copy(username = username) } },
                        modifier = Modifier.weight(1f)
                    )
                    ServersField(
                        label = "Password",
                        value = draft.password,
                        onValueChange = { password -> update { it.copy(password = password) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                ServersField(
                    label = "Host key SHA256 • optional",
                    value = draft.sshHostKeySha256,
                    onValueChange = { key -> update { it.copy(sshHostKeySha256 = key) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    trx("TCP via protected loopback; UDP blocked."),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            ManualProtocol.WIREGUARD -> {
                ServersField(
                    label = "Private key",
                    value = draft.wireguardSecretKey,
                    onValueChange = { key -> update { it.copy(wireguardSecretKey = key) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "Local address / CIDR",
                    value = draft.wireguardAddress,
                    onValueChange = { address -> update { it.copy(wireguardAddress = address) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "Peer public key",
                    value = draft.wireguardPeerPublicKey,
                    onValueChange = { key -> update { it.copy(wireguardPeerPublicKey = key) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "Pre-shared key",
                    value = draft.wireguardPreSharedKey,
                    onValueChange = { key -> update { it.copy(wireguardPreSharedKey = key) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "Allowed IPs",
                    value = draft.wireguardAllowedIps,
                    onValueChange = { ips -> update { it.copy(wireguardAllowedIps = ips) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServersField(
                        label = "Reserved",
                        value = draft.wireguardReserved,
                        onValueChange = { value -> update { it.copy(wireguardReserved = value) } },
                        modifier = Modifier.weight(1f)
                    )
                    ServersField(
                        label = "Keepalive",
                        value = draft.wireguardKeepAlive,
                        onValueChange = { value -> update { it.copy(wireguardKeepAlive = value) } },
                        modifier = Modifier.weight(1f)
                    )
                    ServersField(
                        label = "MTU",
                        value = draft.wireguardMtu,
                        onValueChange = { value -> update { it.copy(wireguardMtu = value) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                ServersInsecureSwitch(
                    checked = draft.wireguardNoKernelTun,
                    label = "Userspace WireGuard",
                    onToggle = { update { it.copy(wireguardNoKernelTun = !it.wireguardNoKernelTun) } }
                )
            }
        }

        if (stream) {
            ServersDropdownField(
                label = "Transport",
                value = draft.transport,
                options = SERVERS_TRANSPORTS.map { ServersOption(it, it.uppercase()) },
                onPick = { transport ->
                    update {
                        it.copy(
                            transport = transport,
                            security = if (it.security == "reality" &&
                                transport !in setOf("raw", "xhttp", "grpc")
                            ) {
                                "tls"
                            } else {
                                it.security
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (draft.transport in setOf("websocket", "xhttp", "httpupgrade")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServersField(
                        label = "Path",
                        value = draft.path,
                        onValueChange = { path -> update { it.copy(path = path) } },
                        modifier = Modifier.weight(1f)
                    )
                    ServersField(
                        label = "Host header",
                        value = draft.hostHeader,
                        onValueChange = { header -> update { it.copy(hostHeader = header) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (draft.transport == "grpc") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServersField(
                        label = "Service name",
                        value = draft.serviceName,
                        onValueChange = { name -> update { it.copy(serviceName = name) } },
                        modifier = Modifier.weight(1f)
                    )
                    ServersField(
                        label = "Authority",
                        value = draft.hostHeader,
                        onValueChange = { header -> update { it.copy(hostHeader = header) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val securityChoices = when {
                protocol == ManualProtocol.TROJAN && realityCapable -> listOf("tls", "reality")
                protocol == ManualProtocol.TROJAN -> listOf("tls")
                realityCapable -> listOf("none", "tls", "reality")
                else -> listOf("none", "tls")
            }
            ServersDropdownField(
                label = "Security",
                value = draft.security,
                options = securityChoices.map { ServersOption(it, it.uppercase()) },
                onPick = { security -> update { it.copy(security = security) } },
                modifier = Modifier.fillMaxWidth()
            )

            if (draft.security in setOf("tls", "reality")) {
                ServersField(
                    label = "SNI",
                    value = draft.sni,
                    onValueChange = { sni -> update { it.copy(sni = sni) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersDropdownField(
                    label = "Fingerprint",
                    value = draft.fingerprint,
                    options = SERVERS_FINGERPRINTS.map { ServersOption(it) },
                    onPick = { fingerprint -> update { it.copy(fingerprint = fingerprint) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (draft.security == "tls") {
                ServersField(
                    label = "ALPN",
                    value = draft.alpn,
                    onValueChange = { alpn -> update { it.copy(alpn = alpn) } },
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "Cipher suites (: separated)",
                    value = draft.cipherSuites,
                    onValueChange = { suites -> update { it.copy(cipherSuites = suites) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    trx("`unsafe` uses native Go TLS; empty Cipher Suites = automatic."),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
                ServersInsecureSwitch(draft.allowInsecure) {
                    update { it.copy(allowInsecure = !it.allowInsecure) }
                }
            }
            if (draft.security == "reality") {
                ServersField(
                    label = "REALITY public key",
                    value = draft.realityPublicKey,
                    onValueChange = { key -> update { it.copy(realityPublicKey = key) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServersField(
                        label = "Short ID",
                        value = draft.realityShortId,
                        onValueChange = { shortId -> update { it.copy(realityShortId = shortId) } },
                        modifier = Modifier.weight(1f)
                    )
                    ServersField(
                        label = "Spider X",
                        value = draft.realitySpiderX,
                        onValueChange = { spider -> update { it.copy(realitySpiderX = spider) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (targetOptions.size > 1) {
            ServersDropdownField(
                label = "Save to",
                value = target,
                options = targetOptions.map { ServersOption(it.first, it.second) },
                onPick = onTargetChange,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            ServersField(
                label = "Save to",
                value = targetName ?: "Manual",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth()
            )
        }

        // The exact reason Save is unavailable — the builder's own words, not a guess.
        Text(
            when {
                targetName == null -> trx("Select a group that can hold this server first.")
                missing == null -> trx("Will be added to") + " " + targetName +
                    trx(" and kept across refreshes.")
                else -> trx(missing)
            },
            color = if (missing == null && targetName != null) Aether.Emerald else Aether.Amber,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2
        )

        CyberButton(
            label = if (protocol == ManualProtocol.SSH) "Save SSH connection" else "Save",
            color = if (missing == null && targetName != null) Aether.Emerald else Aether.InkMuted,
            variant = PrismButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            enabled = !repo.busy && targetName != null && missing == null
        ) {
            onSave(draft, target)
        }
    }
}

/** One labelled on/off row inside the Add-node form. */
@Composable
private fun ServersInsecureSwitch(
    checked: Boolean,
    label: String = "Allow insecure certificate",
    onToggle: () -> Unit
) {
    val description = trx(label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Aether.Amber,
                checkedTrackColor = Aether.Amber.copy(alpha = .24f),
                uncheckedThumbColor = Aether.InkMuted
            )
        )
        Text(
            description,
            color = if (checked) Aether.Amber else Aether.InkMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2
        )
    }
}

/** A credential field with its own reveal control. */
@Composable
private fun ServersSecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggle: () -> Unit
) {
    val description = trx(if (visible) "Hide" else "Show")
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(trx(label)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        shape = ServersCardShape,
        colors = marbleOutlinedTextFieldColors(),
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = description }
                    .kineticClickable(
                        role = Role.Button,
                        boundedShape = CircleShape,
                        showIndication = false,
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(
                    if (visible) HomeIcon.STOP else HomeIcon.DETAILS,
                    Aether.InkFaint,
                    Modifier.size(15.dp)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Add servers: a full-screen sheet with rounded top corners.
 *
 * Three ways in — one node at a time, a proxy chain, or a subscription — plus paste and file
 * import, all landing in the group the page is currently showing.
 */
@Composable
private fun ServersAddPage(
    repo: AppRepository,
    initialMode: String,
    onImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    // MARBLE_ADD_SERVER_MENU_V121 — the menu already asked what the user wants to build, so the
    // form opens on that exact mode. The chips remain so a change of mind costs one tap.
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var subName by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(libraryIntakeTarget(repo)) }
    val clipboard = LocalClipboardManager.current
    // MARBLE_SMART_INTAKE_V122 — the name is optional (the repository picks a readable one);
    // only a real HTTPS subscription URL arms the button, matching what addSubscription accepts.
    val subscriptionReady = subUrl.trim().startsWith("https://", ignoreCase = true)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // A little scrim above the sheet so its rounded top corners read as a sheet.
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Aether.Void)
                    .border(
                        1.dp,
                        Aether.GlassBorderSoft,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when (mode) {
                                "chain" -> trx("Add chain")
                                "subscription" -> trx("Add subscription")
                                else -> trx("Add node")
                            },
                            color = Aether.Ink,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            trx("Build a server, chain several hops, or import a list."),
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    PrismIconButton(
                        onClick = onDismiss,
                        size = 38.dp,
                        descriptiveLabel = "Close"
                    ) {
                        HomeVectorIcon(HomeIcon.CANCEL, Aether.Ink, Modifier.size(16.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "node" to "Node",
                        "chain" to "Chain",
                        "subscription" to "Subscription"
                    ).forEach { (key, label) ->
                        Box(Modifier.weight(1f)) {
                            CyberChoiceChip(
                                text = label,
                                selected = mode == key,
                                color = Aether.Cyan
                            ) { mode = key }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(MarbleSpacing.M)
                ) {
                    when (mode) {
                        // The chain editor owns its own hop list and saves through the
                        // repository, so it only needs the group it is writing into.
                        "chain" -> ManualChainEditor(repo, target, onDismiss)

                        "subscription" -> Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.M)) {
                            OutlinedTextField(
                                value = subName,
                                onValueChange = { subName = it },
                                label = { Text(trx("Source name")) },
                                singleLine = true,
                                shape = ServersCardShape,
                                colors = marbleOutlinedTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = subUrl,
                                onValueChange = { subUrl = it },
                                label = { Text(trx("Subscription URL")) },
                                singleLine = true,
                                shape = ServersCardShape,
                                colors = marbleOutlinedTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            CyberButton(
                                label = "Add subscription",
                                color = Aether.Emerald,
                                variant = PrismButtonVariant.Primary,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = subscriptionReady && !repo.busy
                            ) {
                                // The repository refreshes a remote source on its own and
                                // reports every refusal through the runtime message.
                                repo.addSubscription(subName, subUrl)
                                subName = ""
                                subUrl = ""
                                onDismiss()
                            }
                        }

                        else -> ServersAddNodeForm(
                            repo = repo,
                            target = target,
                            onTargetChange = { target = it },
                            onSave = { draft, targetId ->
                                if (repo.addManualProfile(draft, targetId)) {
                                    // Land the page on the group that just received the node.
                                    repo.selectLibrarySource(targetId)
                                    onDismiss()
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = Aether.GlassBorderSoft)

                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(trx("Import text")) },
                        placeholder = { Text("vless://…  vmess://…  ss://…  trojan://…") },
                        minLines = 3,
                        maxLines = 8,
                        shape = ServersCardShape,
                        colors = marbleOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CyberButton(
                            label = "Paste",
                            color = Aether.InkMuted,
                            modifier = Modifier.weight(1f)
                        ) { importText = clipboard.getText()?.text.orEmpty() }
                        CyberButton(
                            label = "Import file",
                            color = Aether.Amethyst,
                            modifier = Modifier.weight(1f)
                        ) { onImportFile() }
                        CyberButton(
                            label = "Import",
                            color = Aether.Cyan,
                            modifier = Modifier.weight(1f),
                            enabled = !repo.busy && importText.isNotBlank()
                        ) {
                            // MARBLE_SMART_INTAKE_V122 — a pasted subscription URL becomes a
                            // real subscription instead of a bogus proxy profile.
                            val landing = repo.intakeTargetOrManual(target)
                            val addedId = repo.importClipboard(importText, landing)
                            importText = ""
                            repo.selectLibrarySource(addedId ?: landing)
                            onDismiss()
                        }
                    }
                    // MARBLE_MANUAL_BUCKET_V122 — the local bucket is permanent; say where the
                    // import lands instead of gating anything behind a setting.
                    Text(
                        trx("Imports land in") + " " +
                            (repo.subscriptions.firstOrNull { it.id == target }?.name ?: "Manual") +
                            trx(" and are kept across refreshes."),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

// =============================================================================
// Shared surfaces the rest of the app reaches into: field colours, the chain editor,
// the detail page and the full filter sheet.
// =============================================================================

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
private fun ManualChainEditor(
    repo: AppRepository,
    targetSourceId: String,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var hops by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    // MARBLE_MANUAL_BUCKET_V122 — Manual is always a valid destination.
    val targetReady = targetSourceId == "manual" ||
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
            Text(trx("Select one server source first."), color = Aether.Amber, style = MaterialTheme.typography.bodySmall)
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
                    // MARBLE_MANUAL_BUCKET_V122 — the local bucket is permanent.
                    item("source-manual") {
                        SourceOrbitChip(
                            title="Manual",
                            detail="$manualCount servers",
                            selected=sourceFilter == "manual",
                            color=Aether.Amber,
                            onClick={ onSourceFilter("manual") }
                        )
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


// Two small helpers the full filter sheet still leans on: the source chip and the
// human label of each sort mode.

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


private fun sortModeLabel(mode: NodeSortMode): String = when (mode) {
    NodeSortMode.DEFAULT -> "Default"
    NodeSortMode.PING -> "Ping"
    NodeSortMode.SCORE -> "Score"
    NodeSortMode.NAME -> "Name"
    NodeSortMode.PROTOCOL -> "Protocol"
    NodeSortMode.SOURCE -> "Source"
    NodeSortMode.COUNTRY -> "Country"
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


// =================================================================================================
// SETTINGS / SWIPEABLE WORKSPACES
// =================================================================================================

private enum class SettingsWorkspaceTab(val label: String, val icon: HomeIcon) {
    GENERAL("General", HomeIcon.SPARK),
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
    listState: LazyListState = rememberLazyListState(),
    content: @Composable ColumnScope.() -> Unit
) {
    // MARBLE_SETTINGS_RESTORE_V117 — the scroll position is owned by the caller (the settings
    // navigator) and survives the sub-page's leave/re-enter lifecycle, so returning to a page you
    // scrolled never dumps you back at the top.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        state = listState,
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
                    HomeStyle.IOS_SLIDER -> Aether.Emerald
                    HomeStyle.IOS_FLOATING -> Aether.CyanBright
                    HomeStyle.IOS_EMBOSSED -> Aether.AmethystBright
                    HomeStyle.IOS_MODULAR -> Aether.Amber
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
            // Theme 1: Slide track at bottom + top status
            HomeStyle.IOS_SLIDER -> {
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.15f), Size(w * 0.70f, h * 0.25f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.25f), Offset(w * 0.15f, h * 0.45f), Size(w * 0.70f, h * 0.20f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone, Offset(w * 0.15f, h * 0.75f), Size(w * 0.70f, h * 0.20f), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
            }
            // Theme 2: Top status + center server box + right floating dot
            HomeStyle.IOS_FLOATING -> {
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.15f), Size(w * 0.70f, h * 0.25f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.25f), Offset(w * 0.15f, h * 0.45f), Size(w * 0.55f, h * 0.45f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawCircle(tone, h * 0.15f, Offset(w * 0.82f, h * 0.75f))
            }
            // Theme 3: Top status + center bold circle + bottom server box
            HomeStyle.IOS_EMBOSSED -> {
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.12f), Size(w * 0.70f, h * 0.20f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawCircle(tone, h * 0.22f, Offset(w * 0.50f, h * 0.52f))
                drawRoundRect(tone.copy(alpha = 0.25f), Offset(w * 0.15f, h * 0.78f), Size(w * 0.70f, h * 0.18f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            }
            // Theme 4: 2x2 modular tiles
            HomeStyle.IOS_MODULAR -> {
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.15f), Size(w * 0.32f, h * 0.32f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.53f, h * 0.15f), Size(w * 0.32f, h * 0.32f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.55f), Size(w * 0.32f, h * 0.32f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone, Offset(w * 0.53f, h * 0.55f), Size(w * 0.32f, h * 0.32f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
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
    onNavigate: (String) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val t = Tr.now
    val settings = repo.settings
    val activeTheme = parseAppTheme(settings.theme)
    val activeStyle = parseHomeStyle(settings.homeStyle)
    val activeFont = parseAppFont(settings.fontFamily)
    val activeLanguage = parseAppLanguage(settings.appLanguage)

    // MARBLE_SETTINGS_RESTORE_V117 — the hub owns its scroll so coming back from a sub-page lands
    // exactly where you left it instead of reshooting to the top.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        state = listState,
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
            }
        }

    // ------------------------------------------------ Connection
        item(key = "hub-connection") {
            SettingsHubCard(
                title = t.categoryConnection,
                subtitle = "Routing, tests and servers",
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
    }
}

// ---------------------------------------------------------------------------------------------
// Sub-pages
// ---------------------------------------------------------------------------------------------

/** Theme & colours: the full-size previews, plus the frame personality and studio accent. */
@Composable
private fun SettingsThemePage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val t = Tr.now
    SettingsSubPage(
        title = "Theme",
        subtitle = t.themeDetail,
        onBack = onBack,
        listState = listState
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

@Composable
private fun ThemePreviewIllustration(
    style: HomeStyle,
    tone: Color,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val dangerColor = Aether.Danger
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = 8.dp.toPx()

        // Mini phone frame
        drawRoundRect(
            color = Color(0xFF10141E),
            size = Size(w, h),
            cornerRadius = CornerRadius(r, r)
        )
        drawRoundRect(
            color = if (selected) tone.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
            size = Size(w, h),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(if (selected) 2.dp.toPx() else 1.dp.toPx())
        )

        val pad = 5.dp.toPx()
        val contentW = w - pad * 2

        // 1. Top Wide Status Bar
        val statusH = h * 0.22f
        drawRoundRect(
            color = Color(0xFF1B2234),
            topLeft = Offset(pad, pad),
            size = Size(contentW, statusH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        // Status dot
        drawCircle(
            color = tone,
            radius = 2.dp.toPx(),
            center = Offset(pad + 5.dp.toPx(), pad + 5.dp.toPx())
        )
        // Status bar mini lines
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(pad + 11.dp.toPx(), pad + 5.dp.toPx()),
            end = Offset(pad + 32.dp.toPx(), pad + 5.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(pad + 5.dp.toPx(), pad + 11.dp.toPx()),
            end = Offset(pad + contentW - 5.dp.toPx(), pad + 11.dp.toPx()),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round
        )

        when (style) {
            HomeStyle.IOS_SLIDER -> {
                // Center: Sub & Server Box
                val boxY = pad + statusH + 3.dp.toPx()
                val boxH = h * 0.46f
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, boxY),
                    size = Size(contentW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                // Centered Sub Pill
                drawRoundRect(
                    color = tone.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.5f - 14.dp.toPx(), boxY + 3.dp.toPx()),
                    size = Size(28.dp.toPx(), 5.dp.toPx()),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                )
                // 3 Server rows
                for (i in 0..2) {
                    val rowY = boxY + 10.dp.toPx() + i * 7.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (i == 0) 0.16f else 0.07f),
                        topLeft = Offset(pad + 3.dp.toPx(), rowY),
                        size = Size(contentW - 6.dp.toPx(), 5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                // Bottom: Slide to connect track
                val slideY = h - pad - 11.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF1B2234),
                    topLeft = Offset(pad, slideY),
                    size = Size(contentW, 11.dp.toPx()),
                    cornerRadius = CornerRadius(5.5.dp.toPx(), 5.5.dp.toPx())
                )
                drawCircle(
                    color = tone,
                    radius = 4.5.dp.toPx(),
                    center = Offset(pad + 5.5.dp.toPx(), slideY + 5.5.dp.toPx())
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(pad + 14.dp.toPx(), slideY + 5.5.dp.toPx()),
                    end = Offset(pad + contentW - 6.dp.toPx(), slideY + 5.5.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            HomeStyle.IOS_FLOATING -> {
                // Expanded Server Box in Center/Bottom
                val boxY = pad + statusH + 3.dp.toPx()
                val boxH = h - boxY - pad
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, boxY),
                    size = Size(contentW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                // Centered Sub Pill
                drawRoundRect(
                    color = tone.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.5f - 14.dp.toPx(), boxY + 3.dp.toPx()),
                    size = Size(28.dp.toPx(), 5.dp.toPx()),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                )
                // Server rows
                for (i in 0..3) {
                    val rowY = boxY + 10.dp.toPx() + i * 7.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (i == 0) 0.16f else 0.07f),
                        topLeft = Offset(pad + 3.dp.toPx(), rowY),
                        size = Size(contentW - 6.dp.toPx(), 5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                // Right Floating Split Buttons
                val fabX = w - pad - 6.dp.toPx()
                val fab1Y = h - pad - 16.dp.toPx()
                val fab2Y = h - pad - 6.dp.toPx()
                drawCircle(color = dangerColor, radius = 4.dp.toPx(), center = Offset(fabX, fab1Y))
                drawCircle(color = tone, radius = 4.dp.toPx(), center = Offset(fabX, fab2Y))
            }

            HomeStyle.IOS_EMBOSSED -> {
                // Center Bold Embossed Button
                val btnY = pad + statusH + 14.dp.toPx()
                drawCircle(
                    color = tone.copy(alpha = 0.25f),
                    radius = 13.dp.toPx(),
                    center = Offset(w * 0.5f, btnY)
                )
                drawCircle(
                    color = tone,
                    radius = 10.dp.toPx(),
                    center = Offset(w * 0.5f, btnY)
                )

                // Bottom: Server Box
                val boxY = btnY + 16.dp.toPx()
                val boxH = h - boxY - pad
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, boxY),
                    size = Size(contentW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                for (i in 0..1) {
                    val rowY = boxY + 3.dp.toPx() + i * 7.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(pad + 3.dp.toPx(), rowY),
                        size = Size(contentW - 6.dp.toPx(), 5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }

            HomeStyle.IOS_MODULAR -> {
                // Modular Grid: 4 tiles
                val gridY = pad + statusH + 3.dp.toPx()
                val gridH = (h - gridY - pad - 3.dp.toPx()) / 2f
                val gridW = (contentW - 3.dp.toPx()) / 2f

                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, gridY),
                    size = Size(gridW, gridH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad + gridW + 3.dp.toPx(), gridY),
                    size = Size(gridW, gridH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, gridY + gridH + 3.dp.toPx()),
                    size = Size(gridW, gridH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = tone.copy(alpha = 0.25f),
                    topLeft = Offset(pad + gridW + 3.dp.toPx(), gridY + gridH + 3.dp.toPx()),
                    size = Size(gridW, gridH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
    }
}

/** Home style: one thumbnail per presentation, drawn from the style's own artwork. */
@Composable
private fun SettingsHomeStylePage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val t = Tr.now
    val active = parseHomeStyle(repo.settings.homeStyle)
    SettingsSubPage(
        title = t.homeStyleTitle,
        subtitle = t.homeStyleDetail,
        onBack = onBack,
        listState = listState
    ) {
        SettingsHubCard(title = t.homeStyleTitle, subtitle = t.homeStyleDetail, tone = Aether.Cyan) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeStyle.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        row.forEach { style ->
                            val selected = active == style
                            val tone = when (style) {
                                HomeStyle.IOS_SLIDER -> Aether.Emerald
                                HomeStyle.IOS_FLOATING -> Aether.CyanBright
                                HomeStyle.IOS_EMBOSSED -> Aether.AmethystBright
                                HomeStyle.IOS_MODULAR -> Aether.Amber
                            }
                            val shape = RoundedCornerShape(16.dp)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(Aether.Glass.copy(alpha = .42f))
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) tone else Aether.GlassBorderSoft.copy(alpha = .5f),
                                        shape
                                    )
                                    .kineticClickable(role = Role.Button, boundedShape = shape) {
                                        repo.updateSettings(repo.settings.copy(homeStyle = style.id))
                                    }
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ThemePreviewIllustration(
                                    style = style,
                                    tone = tone,
                                    selected = selected,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(78.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        homeStyleLabel(style),
                                        color = if (selected) tone else Aether.Ink,
                                        style = settingsRowTitleStyle(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        homeStyleDetail(style),
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
        // MARBLE_CONNECT_BUTTON_V121 — three connect buttons, one product decision.
        //
        // The old six-way picker mixed a meta choice ("Auto") with five decorations of the same
        // circle, so five of the six looked identical on a phone. What is left are three genuinely
        // different controls: the large round shutter (the product default), a slide-to-connect
        // safety switch and the classic rectangular power switch.
        // MARBLE_CONNECT_PLACEMENT_V123 — each silhouette renders in every Home presentation at
        // the position its own metaphor deserves: the shutter centred in the hero, the slide
        // track docked at the hero floor and the classic power bar docked beneath the instrument.
        SettingsHubCard(
            title = trx("Connect button"),
            subtitle = trx("One control for every Home style"),
            tone = Aether.Cyan
        ) {
            val chosen = parseConnectButtonStyle(repo.settings.connectButtonStyle)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ConnectButtonStyle.entries.forEach { style ->
                    val selected = chosen == style
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Aether.Glass.copy(alpha = .42f))
                            .border(
                                1.dp,
                                if (selected) Aether.Cyan.copy(alpha = .58f)
                                else Aether.GlassBorderSoft.copy(alpha = .5f),
                                shape
                            )
                            .kineticClickable(role = Role.Button, boundedShape = shape) {
                                repo.updateSettings(
                                    repo.settings.copy(connectButtonStyle = style.id)
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsConnectButtonMotif(
                            style = style,
                            tone = if (selected) Aether.Cyan else Aether.InkMuted,
                            modifier = Modifier
                                .width(54.dp)
                                .height(34.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                trx(connectButtonStyleLabel(style)),
                                color = if (selected) Aether.Cyan else Aether.Ink,
                                style = settingsRowTitleStyle(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                trx(connectButtonStyleDetail(style)),
                                color = Aether.InkFaint,
                                style = settingsBodyStyle(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Aether.Cyan
                                    else Aether.InkFaint.copy(alpha = .30f)
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * MARBLE_CONNECT_BUTTON_V121 — the name of each connection control.
 * MARBLE_HOME_V137 — the three hero styles read as Classic / Swipe / Floating; the stream bar
 * and the classic power switch stay as full alternatives. Same VPN logic behind all five.
 */
private fun connectButtonStyleLabel(style: ConnectButtonStyle): String = when (style) {
    ConnectButtonStyle.ROUND -> "Classic"
    ConnectButtonStyle.SLIDE -> "Swipe to connect"
    ConnectButtonStyle.CLASSIC -> "Classic switch"
    ConnectButtonStyle.STREAM -> "Stream bar"
    ConnectButtonStyle.FLOATING -> "Floating button"
}

private fun connectButtonStyleDetail(style: ConnectButtonStyle): String = when (style) {
    ConnectButtonStyle.ROUND -> "Large round power button, centred in the hero (default)"
    ConnectButtonStyle.SLIDE -> "Bottom drag track with threshold, haptics and spring-back"
    ConnectButtonStyle.CLASSIC -> "Classic power bar, docked under the instrument"
    ConnectButtonStyle.STREAM -> "Full-width bar at the page floor with a light band moving right to left"
    ConnectButtonStyle.FLOATING -> "Circular v2rayNG-style button pinned to the bottom corner"
}

/**
 * A still miniature of each connection control at its own placement, so the choice is
 * recognizable at a glance. MARBLE_CONNECT_PLACEMENT_V123 — the faint baseline under each
 * silhouette shows where it sits in the Home presentation: centred (shutter), floor (slide)
 * or docked (classic bar).
 */
@Composable
private fun SettingsConnectButtonMotif(
    style: ConnectButtonStyle,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val baseline = h * .93f
        when (style) {
            ConnectButtonStyle.ROUND -> {
                val c = Offset(w * .5f, h * .44f)
                val r = h * .38f
                drawCircle(tone.copy(alpha = .22f), r, c)
                drawCircle(tone.copy(alpha = .70f), r, c, style = Stroke(1.4.dp.toPx()))
                drawLine(
                    tone,
                    Offset(c.x, c.y - h * .19f),
                    Offset(c.x, c.y + h * .06f),
                    2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    tone.copy(alpha = .28f),
                    Offset(0f, baseline),
                    Offset(w, baseline),
                    1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            ConnectButtonStyle.SLIDE -> {
                val r = h * .30f
                drawRoundRect(
                    color = tone.copy(alpha = .18f),
                    topLeft = Offset(0f, h * .46f - r),
                    size = Size(w, r * 2f),
                    cornerRadius = CornerRadius(r, r)
                )
                drawRoundRect(
                    color = tone.copy(alpha = .60f),
                    topLeft = Offset(0f, h * .46f - r),
                    size = Size(w, r * 2f),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(1.2.dp.toPx())
                )
                drawCircle(tone, r * .74f, Offset(r + 1.dp.toPx(), h * .46f))
                drawLine(
                    tone.copy(alpha = .55f),
                    Offset(w * .55f, h * .46f),
                    Offset(w * .84f, h * .46f),
                    1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    tone.copy(alpha = .28f),
                    Offset(0f, baseline),
                    Offset(w, baseline),
                    1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            ConnectButtonStyle.STREAM -> {
                // A floor bar: full width, with the travelling band drawn as an off-centre
                // highlight and a small arrow echoing its right-to-left direction.
                val r = h * .26f
                val trackTop = h * .46f - r
                drawRoundRect(
                    color = tone.copy(alpha = .18f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(w, r * 2f),
                    cornerRadius = CornerRadius(r, r)
                )
                drawRoundRect(
                    color = tone.copy(alpha = .60f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(w, r * 2f),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(1.2.dp.toPx())
                )
                // The band, drawn mid-travel: a soft block that has entered from the right.
                val bandWidth = w * .34f
                val bandX = w * .58f
                drawRoundRect(
                    color = tone.copy(alpha = .34f),
                    topLeft = Offset(bandX - bandWidth / 2f, trackTop),
                    size = Size(bandWidth, r * 2f),
                    cornerRadius = CornerRadius(r, r)
                )
                // Direction arrow, right to left.
                drawLine(
                    tone,
                    Offset(w * .84f, h * .46f),
                    Offset(w * .62f, h * .46f),
                    1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    tone,
                    Offset(w * .62f, h * .46f),
                    Offset(w * .69f, h * .40f),
                    1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    tone,
                    Offset(w * .62f, h * .46f),
                    Offset(w * .69f, h * .52f),
                    1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            ConnectButtonStyle.FLOATING -> {
                // MARBLE_HOME_V137 — a circular shutter pinned to the bottom-end corner: the
                // circle sits right, the dock line sits below it.
                val r = h * .36f
                val c = Offset(w * .74f, h * .44f)
                drawCircle(tone.copy(alpha = .22f), r, c)
                drawCircle(tone.copy(alpha = .70f), r, c, style = Stroke(1.3.dp.toPx()))
                drawLine(
                    tone,
                    Offset(c.x, c.y - r * .44f),
                    Offset(c.x, c.y + r * .22f),
                    1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    tone.copy(alpha = .28f),
                    Offset(0f, baseline),
                    Offset(w, baseline),
                    1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            ConnectButtonStyle.CLASSIC -> {
                // A looser outer capsule implies the power-deck chrome it docks in.
                drawRoundRect(
                    color = tone.copy(alpha = .10f),
                    topLeft = Offset(0f, h * .08f),
                    size = Size(w, h * .78f),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawRoundRect(
                    color = tone.copy(alpha = .18f),
                    topLeft = Offset(0f, h * .24f),
                    size = Size(w, h * .46f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = tone.copy(alpha = .60f),
                    topLeft = Offset(0f, h * .24f),
                    size = Size(w, h * .46f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(1.2.dp.toPx())
                )
                drawLine(
                    tone,
                    Offset(w * .26f, h * .36f),
                    Offset(w * .26f, h * .56f),
                    2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(tone, 2.dp.toPx(), Offset(w * .78f, h * .47f))
                drawLine(
                    tone.copy(alpha = .28f),
                    Offset(0f, baseline),
                    Offset(w, baseline),
                    1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** Typeface: every candidate rendered in its own face. */
@Composable
private fun SettingsTypefacePage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val t = Tr.now
    SettingsSubPage(
        title = "Typeface",
        subtitle = "The Latin face of the whole product",
        onBack = onBack,
        listState = listState
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
private fun SettingsLanguagePage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val t = Tr.now
    val selectedLanguage = parseAppLanguage(repo.settings.appLanguage)
    SettingsSubPage(
        title = t.languageTitle,
        subtitle = t.languageDetail,
        onBack = onBack,
        listState = listState
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
private fun SettingsInformationPage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val t = Tr.now
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    SettingsSubPage(
        title = t.informationTitle,
        subtitle = t.informationDetail,
        onBack = onBack,
        listState = listState
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
    // MARBLE_SETTINGS_RESTORE_V117 — the starting page is the one the user last visited (restored
    // from persistence), so reopening the Settings tab after a Theme visit returns to the Theme page
    // instead of reshooting to the top of the hub.
    var page by rememberSaveable {
        mutableStateOf(repo.lastSettingsPage.ifBlank { SettingsPages.HUB })
    }

    // MARBLE_SETTINGS_RESTORE_V117 — the scroll position of the hub (and the workspace pages) is
    // owned here, above the page switch, so it is never torn down with a page's composition. Each
    // workspace tab gets its own slot in the persistent map so switching between pages keeps their
    // places for the whole session.
    // MARBLE_SETTINGS_RESTORE_V117 — scroll states are owned here, above the page switch, so the
    // hub and each dedicated sub-page never lose their place when the user navigates away and back.
    val hubListState = rememberLazyListState()
    val themeListState = rememberLazyListState()
    val homeStyleListState = rememberLazyListState()
    val typefaceListState = rememberLazyListState()
    val languageListState = rememberLazyListState()
    val informationListState = rememberLazyListState()
    // One scroll state per workspace tab; only the active tab's is shown at a time.
    val workspaceListStates = remember {
        SettingsWorkspaceTab.entries.associateWith { LazyListState() }
    }
    fun workspaceListState(tab: SettingsWorkspaceTab): LazyListState =
        workspaceListStates.getValue(tab)

    // Persist the current page so the next visit restores it.
    LaunchedEffect(page) {
        repo.rememberSettingsPage(page)
    }

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
                onNavigate = { page = it },
                listState = hubListState
            )

            SettingsPages.isWorkspace(target) -> SettingsTabPage(
                repo = repo,
                tab = SettingsPages.workspaceTab(target),
                focusSection = SettingsPages.workspaceFocus(target),
                listState = workspaceListState(SettingsPages.workspaceTab(target)),
                onBack = { page = SettingsPages.HUB }
            )

            target == SettingsPages.THEME -> SettingsThemePage(
                repo = repo,
                listState = themeListState,
                onBack = { page = SettingsPages.HUB }
            )
            target == SettingsPages.HOME_STYLE ->
                SettingsHomeStylePage(
                    repo = repo,
                    listState = homeStyleListState,
                    onBack = { page = SettingsPages.HUB }
                )

            target == SettingsPages.TYPEFACE ->
                SettingsTypefacePage(
                    repo = repo,
                    listState = typefaceListState,
                    onBack = { page = SettingsPages.HUB }
                )

            target == SettingsPages.LANGUAGE ->
                SettingsLanguagePage(
                    repo = repo,
                    listState = languageListState,
                    onBack = { page = SettingsPages.HUB }
                )

            else -> SettingsInformationPage(
                repo = repo,
                listState = informationListState,
                onBack = { page = SettingsPages.HUB }
            )
        }
    }
}

/**
 * MARBLE_SETTINGS_FLAT_SINGLE_PAGE_V115 — Settings has no tabs and no rail any more. Each hub
 * title opens one dedicated full page that owns its own scroll; the page is the old tab's content
 * (its section cards, expert gating and Routing focus) rendered as a single titled surface. The
 * tab strip and the adaptive rail are gone from the product entirely.
 */
@Composable
private fun SettingsTabPage(
    repo: AppRepository,
    tab: SettingsWorkspaceTab,
    onBack: () -> Unit,
    focusSection: String? = null,
    listState: LazyListState = rememberLazyListState()
) {
    val sections = settingsSections(tab, repo, repo.settings.expertMode, focusSection)
    SettingsSubPage(
        title = settingsTabPageTitle(tab),
        subtitle = settingsTabPageSubtitle(tab),
        onBack = onBack,
        listState = listState
    ) {
        if (sections.isEmpty()) {
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
}

/** The page title of a dedicated Settings page, matching its hub row wording. */
private fun settingsTabPageTitle(tab: SettingsWorkspaceTab): String = when (tab) {
    SettingsWorkspaceTab.GENERAL -> "General"
    SettingsWorkspaceTab.TESTS -> "Tests & ranking"
    SettingsWorkspaceTab.NETWORK -> "Network & routing"
    SettingsWorkspaceTab.ENGINE -> "Engine & tunnel"
    SettingsWorkspaceTab.SYSTEM -> "System"
}

/** The quiet one-line description under the title of a dedicated Settings page. */
private fun settingsTabPageSubtitle(tab: SettingsWorkspaceTab): String = when (tab) {
    SettingsWorkspaceTab.GENERAL -> "Home layout, sources and app updates"
    SettingsWorkspaceTab.TESTS -> "Probes, ranking and live route intelligence"
    SettingsWorkspaceTab.NETWORK -> "DNS, split tunnel and geo rules"
    SettingsWorkspaceTab.ENGINE -> "Xray, transport and adaptive buffers"
    SettingsWorkspaceTab.SYSTEM -> "Notifications, diagnostics and live stats"
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

    return when (tab) {
        SettingsWorkspaceTab.GENERAL -> listOf(
            card(
                "Signature studio",
                "Home layers that live nowhere else",
                HomeIcon.MODE,
                Aether.Cyan
            ) { SignatureStudioSettings(repo) },
            card("Connection","Tunnel, proxy, port",HomeIcon.TUNNEL,Aether.Cyan) { ConnectionSettings(repo) },
            card("Subscriptions","Refresh & sources",HomeIcon.LIBRARY,Aether.Amethyst) { SubscriptionSettings(repo) }
        )
        // MARBLE_SETTINGS_EXPERT_ALWAYS_V118 — Advanced Settings is no longer gated. Expert mode was a
        // switch that hid the low-level tunnel controls; the product owner removed the gating so every
        // option is shown across all sections. The `expertMode` value is kept for persistence/compat and
        // the hub row is retained as a display read-out, but it never hides a card.
        SettingsWorkspaceTab.TESTS -> listOf(
            card(
                "Testing",
                // The one ping method, named on the card so it is visible without opening it.
                "Ping method • ${probeMethodShortLabel(repo.settings.probeMethod)}",
                HomeIcon.BENCHMARK,
                Aether.Amethyst
            ) { ProbeSettings(repo) },
            card("Identity Guard","Keep one public exit",HomeIcon.SHIELD,Aether.Cyan) {
                SettingSwitch(
                    title = "Identity Guard",
                    subtitle = "Pin the session to one exit IP",
                    checked = repo.settings.identityGuardEnabled
                ) { repo.updateSettings(repo.settings.copy(identityGuardEnabled = it)) }
            }
        )
        SettingsWorkspaceTab.NETWORK -> buildList {
            if(routingFocused) {
                add(card("Routing","Geo assets & rules",HomeIcon.ROUTING,Aether.Emerald) { RoutingSettings(repo) })
            }
            add(card("DNS","TUN & DoH",HomeIcon.NETWORK,Aether.Cyan) { DnsSettings(repo) })
            if(!routingFocused) {
                add(card("Routing","Geo assets & rules",HomeIcon.ROUTING,Aether.Emerald) { RoutingSettings(repo) })
            }
            // Per-app proxy moved into Network & Routing — no standalone section, no hub card.
            add(card("Per-app proxy","Tunnel or bypass per app",HomeIcon.PRIVACY,Aether.Emerald) { SplitTunnelSettings(repo) })
        }
        SettingsWorkspaceTab.ENGINE -> listOf(
            card("Fragment & Mux","DPI resilience",HomeIcon.SPARK,Aether.Amber) { FragmentMuxSettings(repo) }
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
    // MARBLE_PRODUCT_SIMPLE_V117 — a calmer, more spacious card: a small tinted icon chip,
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
    HomeStyle.IOS_SLIDER -> Tr.now.styleIosSlider
    HomeStyle.IOS_FLOATING -> Tr.now.styleIosFloating
    HomeStyle.IOS_EMBOSSED -> Tr.now.styleIosEmbossed
    HomeStyle.IOS_MODULAR -> Tr.now.styleIosModular
}

@Composable
private fun homeStyleDetail(style: HomeStyle): String = when (style) {
    HomeStyle.IOS_SLIDER -> Tr.now.styleIosSliderDetail
    HomeStyle.IOS_FLOATING -> Tr.now.styleIosFloatingDetail
    HomeStyle.IOS_EMBOSSED -> Tr.now.styleIosEmbossedDetail
    HomeStyle.IOS_MODULAR -> Tr.now.styleIosModularDetail
}

/**
 * MARBLE_NO_DUPLICATES_V116 — the Signature studio layers that live nowhere else in Settings.
 * Theme, Home style, Typeface and Language all have their own hub pages, so the old combined
 * Appearance block that repeated every one of them is gone. What remains here is the studio
 * configuration only its own page can own: the floating button, the status banner and the corner
 * action cluster.
 *
 * MARBLE_SIGNATURE_STUDIO_TRIM_V121 — the server rail and the style switcher were removed from
 * Home, so their switches are gone from here too.
 */
@Composable
private fun SignatureStudioSettings(repo: AppRepository) {
    val t = Tr.now

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
    // Theme, Home style, Typeface and Language live on the hub's dedicated pages; repeating them
    // here was exactly the duplicate settings the product owner rejected.
    Text(
        trx("Theme, Home style, Typeface and Language live under Appearance on the Settings hub."),
        color = Aether.InkFaint,
        style = MaterialTheme.typography.bodySmall
    )
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
    // MARBLE_PRODUCT_SIMPLE_V117 — flat segmented pills, no elevation.
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
    updatedAt: Long,
    modifier: Modifier = Modifier
) {
    val age = if (updatedAt > 0L) System.currentTimeMillis() - updatedAt else Long.MAX_VALUE
    val stale = !ready || age > com.marbleng.app.model.RoutingDefaults.STALE_ASSET_MS
    val color = when {
        !ready -> Aether.Danger
        stale -> Aether.Amber
        else -> Aether.Emerald
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = if (ready && !stale) .10f else .07f))
            .border(1.dp, color.copy(alpha = .38f), shape)
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(7.dp))
            Text(title, color = Aether.Ink, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    !ready -> "Missing"
                    stale -> "Stale"
                    else -> "Ready"
                },
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (!ready) "Tap Update to download"
            else "${formatBytes(bytes)} • ${relativeTime(updatedAt)}",
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoutingSettings(repo: AppRepository) {
    // MARBLE_ROUTING_UI_V136 — the routing workspace, rebuilt around one honest model:
    // the mode decides the implicit behaviour, the rule list is the user's own ordered policy,
    // the geo databases power live suggestions + validation, and the simulator answers the
    // only question that matters when a site breaks: *which rule did it, and what now?*
    val s = repo.settings
    val assets = repo.routingAssetStatus()
    val rules = remember(s.routingRulesJson) { RoutingEngine.effectiveRules(s) }
    val implicit = remember(s.routingMode, s.routeBlockAds, s.routeAdsTag, s.routeGeoIpTags, s.routeGeoSiteTags, s.routeBypassPrivate) {
        RoutingEngine.implicitRules(s)
    }
    var sourceMenu by remember { mutableStateOf(false) }
    val currentSource = RoutingDefaults.sourceById(s.geoAssetSourceId)

    // Sheet state: null = closed; a rule + isNew flag = the professional editor.
    var editorRule by remember { mutableStateOf<RoutingRule?>(null) }
    var editorIsNew by remember { mutableStateOf(false) }

    // Drag-reorder state (pixel space; heights measured per card).
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current
    val defaultItemHeightPx = with(density) { 96.dp.toPx() }
    fun heightAt(index: Int): Float = itemHeights[index]?.toFloat() ?: defaultItemHeightPx
    val dragTarget = if (dragIndex in rules.indices) {
        routingDragTarget(rules.size, dragIndex, dragOffsetPx, ::heightAt)
    } else {
        -1
    }

    var confirmPreset by remember { mutableStateOf<RoutingPresets.Preset?>(null) }

    // ------------------------------------------------------------------ 0. Master Switch
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Aether.GlassStrong.copy(alpha = .45f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                trx("Enable Custom Routing"),
                color = Aether.Ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                trx("Control traffic routing, domain resolution and rule matching"),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Switch(
            checked = s.customRoutingEnabled,
            onCheckedChange = { repo.updateSettings(s.copy(customRoutingEnabled = it)) },
            colors = marbleSwitchColors()
        )
    }

    // ------------------------------------------------------------------ 1. Routing mode
    Text(trx("Routing mode"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutingModeCard(
            title = "Proxy all",
            detail = "Everything rides the tunnel",
            selected = s.routingMode == RoutingMode.PROXY_ALL,
            tone = Aether.Cyan,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.updateSettings(s.copy(routingMode = RoutingMode.PROXY_ALL)) }
        RoutingModeCard(
            title = "Private direct",
            detail = "LAN never enters the tunnel",
            selected = s.routingMode == RoutingMode.BYPASS_PRIVATE,
            tone = Aether.Emerald,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.updateSettings(s.copy(routingMode = RoutingMode.BYPASS_PRIVATE)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutingModeCard(
            title = "Geo direct",
            detail = "Selected countries bypass the tunnel",
            selected = s.routingMode == RoutingMode.GEO_DIRECT,
            tone = Aether.Emerald,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.updateSettings(s.copy(routingMode = RoutingMode.GEO_DIRECT)) }
        RoutingModeCard(
            title = "Custom",
            detail = "Only your rules decide",
            selected = s.routingMode == RoutingMode.CUSTOM,
            tone = Aether.Amethyst,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.updateSettings(s.copy(routingMode = RoutingMode.CUSTOM)) }
    }
    if (s.routingMode == RoutingMode.GEO_DIRECT &&
        (implicit.directIpTags.isNotEmpty() || implicit.directSiteTags.isNotEmpty())
    ) {
        Text(
            trx("Geo direct sends these straight over the underlay:") + " " +
                (implicit.directIpTags + implicit.directSiteTags).joinToString(", "),
            color = Aether.InkMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }

    // ------------------------------------------------------------------ Strategy
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            trx("Domain resolution strategy"),
            color = Aether.Ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf(
                "IPIfNonMatch" to "IPIfNonMatch (v2rayNG default)",
                "IPOnDemand" to "IPOnDemand",
                "AsIs" to "AsIs (Fastest)"
            ).forEach { (value, _) ->
                CyberChoiceChip(
                    text = value,
                    selected = s.routeDomainStrategy == value,
                    color = Aether.Cyan
                ) { repo.updateSettings(s.copy(routeDomainStrategy = value)) }
            }
        }
        Text(
            trx(
                when (s.routeDomainStrategy) {
                    "IPOnDemand" -> "Resolve whenever an IP rule is met first"
                    "AsIs" -> "Route on the address the app dialled"
                    else -> "Resolve after domain rules miss — geoip rules work on domains"
                }
            ),
            color = Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }

    // ------------------------------------------------------------------ 2. Presets
    Text(trx("Presets"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RoutingPresets.Preset.entries.forEach { preset ->
            CyberChoiceChip(
                text = preset.title,
                selected = rules.map { it.id } == preset.rules.map { it.id },
                color = Aether.Emerald
            ) { confirmPreset = preset }
        }
    }

    // ------------------------------------------------------------------ 3. Geo databases
    Text(trx("Geo data source"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Box {
        CyberButton(
            label = currentSource.label,
            color = Aether.Emerald,
            modifier = Modifier.fillMaxWidth(),
            icon = HomeIcon.GLOBE
        ) { sourceMenu = true }
        DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
            RoutingDefaults.SOURCES.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.label) },
                    onClick = {
                        sourceMenu = false
                        repo.applyGeoAssetSource(source.id)
                    }
                )
            }
        }
    }

    if (currentSource.id == "custom") {
        TinyField("geoip.dat HTTPS URL", s.geoIpUrl, Modifier.fillMaxWidth()) {
            repo.updateSettings(s.copy(geoIpUrl = it))
        }
        TinyField("geosite.dat HTTPS URL", s.geoSiteUrl, Modifier.fillMaxWidth()) {
            repo.updateSettings(s.copy(geoSiteUrl = it))
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutingAssetCard(
            title = "GeoIP",
            ready = assets.geoIpReady,
            bytes = assets.geoIpBytes,
            updatedAt = assets.geoIpUpdatedAt,
            modifier = Modifier.weight(1f)
        )
        RoutingAssetCard(
            title = "GeoSite",
            ready = assets.geoSiteReady,
            bytes = assets.geoSiteBytes,
            updatedAt = assets.geoSiteUpdatedAt,
            modifier = Modifier.weight(1f)
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyberButton("Update", Aether.Cyan, Modifier.weight(1f), !repo.busy) {
            repo.prepareRoutingAssets(true)
        }
        CyberButton("Verify", Aether.Emerald, Modifier.weight(1f), repo.libraryProfiles.isNotEmpty() && !repo.busy) {
            repo.verifyRoutingPolicy()
        }
    }

    // ------------------------------------------------------------------ 4. Switches the mode leaves independent
    SettingSwitch(
        title = "Block ads",
        subtitle = "geosite ad categories never load — in every mode",
        checked = s.routeBlockAds
    ) { repo.updateSettings(s.copy(routeBlockAds = it)) }
    // SettingSwitch has no enabled lane: when the mode pins the bypass on, the switch reads
    // pinned-on and the subtitle says so instead of offering a toggle that cannot do anything.
    SettingSwitch(
        title = "Bypass private LAN",
        subtitle = if (implicit.forceBypassPrivate) "Always on in this mode" else "RFC1918 stays off the tunnel",
        checked = s.routeBypassPrivate || implicit.forceBypassPrivate
    ) { pinnedOn -> repo.updateSettings(s.copy(routeBypassPrivate = pinnedOn || implicit.forceBypassPrivate)) }

    // ------------------------------------------------------------------ 5. The rule list
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                trx("Rules"),
                color = Aether.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                trx("Top wins. Drag to reorder, tap to edit."),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        HoloBadge("${rules.size}", Aether.Cyan, compact = true)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rules.forEachIndexed { index, rule ->
            val displacement = when {
                dragIndex < 0 -> 0f
                index == dragIndex -> 0f
                dragIndex < index && index <= dragTarget -> -heightAt(dragIndex)
                dragTarget <= index && index < dragIndex -> heightAt(dragIndex)
                else -> 0f
            }
            RoutingRuleCard(
                rule = rule,
                repo = repo,
                dragging = dragIndex == index,
                dragOffset = dragOffsetPx,
                displacementPx = displacement,
                onHeight = { itemHeights[index] = it },
                onEdit = {
                    editorRule = rule
                    editorIsNew = false
                },
                onDragStart = {
                    dragIndex = index
                    dragOffsetPx = 0f
                },
                onDrag = { dragOffsetPx += it },
                onDragEnd = {
                    val target = routingDragTarget(rules.size, dragIndex, dragOffsetPx, ::heightAt)
                    if (dragIndex in rules.indices && target in rules.indices && target != dragIndex) {
                        repo.setRoutingRules(RoutingEngine.move(rules, dragIndex, target))
                    }
                    dragIndex = -1
                    dragOffsetPx = 0f
                },
                onDragCancel = {
                    dragIndex = -1
                    dragOffsetPx = 0f
                }
            )
        }
    }

    CyberButton(
        label = "Add rule",
        color = Aether.Cyan,
        modifier = Modifier.fillMaxWidth(),
        icon = HomeIcon.PLUS
    ) {
        editorRule = RoutingEngine.newRule()
        editorIsNew = true
    }

    // ------------------------------------------------------------------ 6. Route simulator
    RoutingSimulatorCard(repo)

    // ------------------------------------------------------------------ 7. Expert
    RoutingExpertSection(repo, s)

    confirmPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { confirmPreset = null },
            title = { Text(trx("Apply ${preset.title} preset?"), color = Aether.Ink) },
            text = {
                Text(
                    trx("This replaces your current rules with the preset list. Your mode, geo source and expert lists stay untouched."),
                    color = Aether.InkMuted
                )
            },
            confirmButton = {
                MarbleDialogAction("Replace", Aether.Emerald, variant = PrismButtonVariant.Primary) {
                    repo.applyRoutingPreset(preset)
                    confirmPreset = null
                }
            },
            dismissButton = {
                MarbleDialogAction("Cancel", Aether.InkMuted) { confirmPreset = null }
            },
            containerColor = Aether.VoidElevated
        )
    }

    editorRule?.let { initial ->
        RoutingRuleEditorSheet(
            repo = repo,
            initial = initial,
            isNew = editorIsNew,
            onDismiss = { editorRule = null }
        )
    }
}

/** Slot under the dragged card's centre — the index a live reorder would land on. */
private fun routingDragTarget(count: Int, from: Int, delta: Float, heightAt: (Int) -> Float): Int {
    if (from !in 0 until count) return from
    val newCenter = run {
        var acc = 0f
        for (j in 0 until from) acc += heightAt(j)
        acc + heightAt(from) / 2f + delta
    }
    var acc = 0f
    for (i in 0 until count) {
        val h = heightAt(i)
        if (newCenter < acc + h) return i
        acc += h
    }
    return count - 1
}

@Composable
private fun RoutingModeCard(
    title: String,
    detail: String,
    selected: Boolean,
    tone: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(15.dp)
    PrismWell(
        modifier = modifier,
        tone = tone,
        selected = selected,
        onClick = if (enabled) onClick else null,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Column {
            Text(
                trx(title),
                color = if (selected) tone else Aether.Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                trx(detail),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RoutingRuleCard(
    rule: RoutingRule,
    repo: AppRepository,
    dragging: Boolean,
    dragOffset: Float,
    displacementPx: Float,
    onHeight: (Int) -> Unit,
    onEdit: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val tone = when (rule.outbound) {
        RoutingOutbound.BLOCK -> Aether.Danger
        RoutingOutbound.DIRECT -> Aether.Emerald
        RoutingOutbound.PROXY -> Aether.Cyan
    }
    val issues = remember(rule) { RoutingEngine.validateRule(rule) }
    val errors = issues.filter { it.severity == RoutingEngine.IssueSeverity.ERROR }
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(15.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onHeight(it.size.height) }
            .graphicsLayer {
                translationY = if (dragging) {
                    // The dragged card follows the finger so the grab never feels binary;
                    // displacement cards get the exact swap offset instead.
                    dragOffset
                } else {
                    displacementPx
                }
                shadowElevation = if (dragging) 18f else 0f
            }
            .zIndex(if (dragging) 1f else 0f)
            .clip(shape)
            .background(
                when {
                    dragging -> Aether.GlassStrong.copy(alpha = .6f)
                    else -> Aether.GlassStrong.copy(alpha = .35f)
                }
            )
            .border(
                1.dp,
                when {
                    errors.isNotEmpty() -> Aether.Danger.copy(alpha = .55f)
                    dragging -> tone.copy(alpha = .6f)
                    else -> tone.copy(alpha = .28f)
                },
                shape
            )
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onEdit)
            .padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The drag handle: a long-press anywhere on it lifts the card, then plain dragging
            // reorders live. Icon-only, so the whole row stays one tap-to-edit surface.
            Box(
                Modifier
                    .size(30.dp, 40.dp)
                    .pointerInput(rule.id, repo.settings.routingRulesJson) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDragStart()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(HomeIcon.SORT, if (dragging) tone else Aether.InkFaint, Modifier.size(15.dp))
            }
            Spacer(Modifier.width(6.dp))
            // Outbound colour spine: one glance says what this rule does.
            Box(
                Modifier
                    .size(3.dp, 34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tone)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    trx(rule.remark.ifBlank { rule.kind.name.lowercase() }),
                    color = if (rule.enabled) Aether.Ink else Aether.InkFaint,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    routingMatcherSummary(rule),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            if (errors.isNotEmpty()) {
                PrismBadge("!", Aether.Danger, strong = true)
                Spacer(Modifier.width(6.dp))
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { on ->
                    val current = RoutingEngine.effectiveRules(repo.settings)
                    val i = current.indexOfFirst { it.id == rule.id }
                    if (i >= 0) repo.setRoutingRules(
                        current.toMutableList().also { it[i] = current[i].copy(enabled = on) }
                    )
                },
                colors = marbleSwitchColors()
            )
            Box {
                var menu by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .kineticClickable(role = Role.Button, showIndication = false) { menu = true },
                    contentAlignment = Alignment.Center
                ) {
                    HomeVectorIcon(HomeIcon.MORE, Aether.InkMuted, Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(trx("Edit")) },
                        onClick = { menu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(trx("Duplicate")) },
                        onClick = {
                            menu = false
                            val current = RoutingEngine.effectiveRules(repo.settings)
                            val i = current.indexOfFirst { it.id == rule.id }
                            if (i >= 0) repo.setRoutingRules(
                                current.toMutableList().also { it.add(i + 1, RoutingEngine.duplicate(rule)) }
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(trx("Move to top")) },
                        onClick = {
                            menu = false
                            val current = RoutingEngine.effectiveRules(repo.settings)
                            val i = current.indexOfFirst { it.id == rule.id }
                            if (i > 0) repo.setRoutingRules(RoutingEngine.move(current, i, 0))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(trx("Move to bottom")) },
                        onClick = {
                            menu = false
                            val current = RoutingEngine.effectiveRules(repo.settings)
                            val i = current.indexOfFirst { it.id == rule.id }
                            if (i in 0 until current.lastIndex) {
                                repo.setRoutingRules(RoutingEngine.move(current, i, current.lastIndex))
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(trx("Delete"), color = Aether.Danger) },
                        onClick = {
                            menu = false
                            repo.setRoutingRules(
                                RoutingEngine.effectiveRules(repo.settings).filterNot { it.id == rule.id }
                            )
                        }
                    )
                }
            }
        }
        if (errors.isNotEmpty()) {
            Text(
                errors.first().message,
                color = Aether.Danger,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun routingMatcherSummary(rule: RoutingRule): String {
    val matcher = rule.matcher.ifBlank { "…" }
    val extras = buildList {
        if (rule.port.isNotBlank() && rule.kind != RoutingRuleKind.PORT) add("port ${rule.port}")
        if (rule.network.isNotBlank()) add(rule.network)
        if (rule.protocol.isNotBlank()) add(rule.protocol)
    }
    val prefix = when (rule.kind) {
        RoutingRuleKind.GEOSITE -> "geosite:"
        RoutingRuleKind.GEOIP -> "geoip:"
        else -> ""
    }
    return (prefix + matcher + extras.joinToString(" • ", prefix = "  •  ")).trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingRuleEditorSheet(
    repo: AppRepository,
    initial: RoutingRule,
    isNew: Boolean,
    onDismiss: () -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val issues = remember(draft) { RoutingEngine.validateRule(draft) }
    val errors = issues.filter { it.severity == RoutingEngine.IssueSeverity.ERROR }
    val preview = remember(draft) { routingRulePreviewJson(draft) }

    // Live suggestions from the geo databases — the app guesses while the user types.
    val suggestions = remember(draft.kind, draft.matcher) {
        when {
            draft.kind == RoutingRuleKind.GEOSITE ->
                GeoAssetIndex.suggest(GeoAssetIndex.Kind.GEOSITE, draft.matcher)
            draft.kind == RoutingRuleKind.GEOIP ->
                GeoAssetIndex.suggest(GeoAssetIndex.Kind.GEOIP, draft.matcher)
            draft.kind == RoutingRuleKind.DOMAIN &&
                draft.matcher.trim().lowercase().startsWith("geosite:") ->
                GeoAssetIndex.suggest(
                    GeoAssetIndex.Kind.GEOSITE,
                    draft.matcher.trim().substringAfter(':')
                )
            else -> emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Aether.InkFaint.copy(alpha = .55f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        trx(if (isNew) "New rule" else "Edit rule"),
                        color = Aether.Ink,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        trx("First matching rule wins"),
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Aether.Cyan.copy(alpha = .085f))
                        .kineticClickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    HomeVectorIcon(HomeIcon.CANCEL, Aether.Cyan, Modifier.size(17.dp))
                }
            }

            // ---- Kind
            Text(trx("What does it match?"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(
                    RoutingRuleKind.DOMAIN to "Domains",
                    RoutingRuleKind.GEOSITE to "GeoSite tag",
                    RoutingRuleKind.GEOIP to "GeoIP tag",
                    RoutingRuleKind.IP to "IP / CIDR",
                    RoutingRuleKind.PORT to "Port"
                ).forEach { (kind, label) ->
                    CyberChoiceChip(
                        text = label,
                        selected = draft.kind == kind,
                        color = Aether.Cyan
                    ) { draft = draft.copy(kind = kind) }
                }
            }
            Text(
                trx(
                    when (draft.kind) {
                        RoutingRuleKind.DOMAIN -> "One entry per token: example.com, domain:example.com, full:host, keyword:text, regexp:pattern — separated by commas"
                        RoutingRuleKind.GEOSITE -> "A category from geosite.dat — pick a suggestion or type to search it live"
                        RoutingRuleKind.GEOIP -> "A country or provider tag from geoip.dat — private expands to all LAN ranges"
                        RoutingRuleKind.IP -> "IPs and ranges: 1.2.3.4, 10.0.0.0/8, 2001:db8::/32, or geoip:private"
                        RoutingRuleKind.PORT -> "Ports: 443, 80,8443 or 1000-2000"
                    }
                ),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall
            )

            // ---- Matcher
            TinyField(
                label = when (draft.kind) {
                    RoutingRuleKind.GEOSITE -> "GeoSite tag"
                    RoutingRuleKind.GEOIP -> "GeoIP tag"
                    RoutingRuleKind.DOMAIN -> "Domains"
                    RoutingRuleKind.IP -> "IPs / CIDRs"
                    RoutingRuleKind.PORT -> "Ports"
                },
                value = draft.matcher,
                modifier = Modifier.fillMaxWidth()
            ) { draft = draft.copy(matcher = it) }

            if (suggestions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { entry ->
                        SuggestionChip(
                            onClick = {
                                val text = if (draft.kind == RoutingRuleKind.DOMAIN) {
                                    "geosite:${entry.tag}"
                                } else {
                                    entry.tag
                                }
                                draft = draft.copy(matcher = text)
                            },
                            label = {
                                Text(
                                    if (entry.count > 0) "${entry.tag} • ${entry.count}" else entry.tag,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Aether.Cyan.copy(alpha = .35f)),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color.Transparent,
                                labelColor = Aether.Ink
                            )
                        )
                    }
                }
            }

            // ---- Refinements
            Text(trx("Refine (optional)"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("" to "Any net", "tcp" to "TCP", "udp" to "UDP").forEach { (value, label) ->
                    CyberChoiceChip(
                        text = label,
                        selected = draft.network.trim().lowercase() == value,
                        color = Aether.Amethyst
                    ) { draft = draft.copy(network = value) }
                }
            }
            if (draft.kind != RoutingRuleKind.PORT) {
                TinyField("Ports (optional) — 443 or 80,8443", draft.port, Modifier.fillMaxWidth()) {
                    draft = draft.copy(port = it.trim())
                }
            }
            TinyField("Protocols (optional) — quic, bittorrent", draft.protocol, Modifier.fillMaxWidth()) {
                draft = draft.copy(protocol = it.trim())
            }

            // ---- Outbound
            Text(trx("Then what?"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    RoutingOutbound.PROXY to Aether.Cyan,
                    RoutingOutbound.DIRECT to Aether.Emerald,
                    RoutingOutbound.BLOCK to Aether.Danger
                ).forEach { (outbound, tone) ->
                    Box(Modifier.weight(1f)) {
                        CyberChoiceChip(
                            text = outbound.name,
                            selected = draft.outbound == outbound,
                            color = tone
                        ) { draft = draft.copy(outbound = outbound) }
                    }
                }
            }

            // ---- Issues
            issues.forEach { issue ->
                Text(
                    issue.message,
                    color = if (issue.severity == RoutingEngine.IssueSeverity.ERROR) Aether.Danger else Aether.Amber,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // ---- Emitted-shape preview
            if (errors.isEmpty()) {
                Text(
                    trx("Emitted Xray rule"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    preview,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton(
                    label = if (isNew) "Add rule" else "Save",
                    color = Aether.Emerald,
                    modifier = Modifier.weight(1f),
                    enabled = errors.isEmpty(),
                    variant = PrismButtonVariant.Primary
                ) {
                    val current = RoutingEngine.effectiveRules(repo.settings)
                    val updated = if (isNew) {
                        current + draft
                    } else {
                        current.map { if (it.id == initial.id) draft else it }
                    }
                    repo.setRoutingRules(updated)
                    onDismiss()
                }
                if (!isNew) {
                    CyberButton(
                        label = "Delete",
                        color = Aether.Danger,
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.setRoutingRules(
                            RoutingEngine.effectiveRules(repo.settings).filterNot { it.id == initial.id }
                        )
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** The exact `type: field` shape this rule becomes in the emitted config. */
private fun routingRulePreviewJson(rule: RoutingRule): String {
    val obj = JSONObject().put("type", "field")
    when (rule.kind) {
        RoutingRuleKind.GEOSITE ->
            RoutingEngine.normalizeGeoSite(rule.matcher)?.let {
                obj.put("domain", JSONArray(listOf(it)))
            }
        RoutingRuleKind.GEOIP ->
            RoutingEngine.normalizeGeoIp(rule.matcher)?.let { token ->
                if (token == "geoip:private") {
                    obj.put("ip", JSONArray(RoutingEngine.PRIVATE_CIDRS))
                } else {
                    obj.put("ip", JSONArray(listOf(token)))
                }
            }
        RoutingRuleKind.DOMAIN -> obj.put("domain", JSONArray(RoutingEngine.splitDomains(rule.matcher)))
        RoutingRuleKind.IP -> obj.put("ip", JSONArray(RoutingEngine.splitIps(rule.matcher)))
        RoutingRuleKind.PORT -> {
            val ports = rule.matcher.trim().ifBlank { rule.port }.trim()
            if (ports.isNotBlank()) obj.put("port", ports)
        }
    }
    val ports = rule.port.trim()
    if (rule.kind != RoutingRuleKind.PORT && ports.isNotBlank()) obj.put("port", ports)
    if (rule.network.isNotBlank()) obj.put("network", rule.network.trim().lowercase())
    val protocols = rule.protocol.split(',').map(String::trim).filter(String::isNotBlank)
    if (protocols.isNotEmpty()) obj.put("protocol", JSONArray(protocols))
    obj.put(
        "outboundTag",
        when (rule.outbound) {
            RoutingOutbound.PROXY -> "<proxy>"
            RoutingOutbound.DIRECT -> "direct"
            RoutingOutbound.BLOCK -> "block"
        }
    )
    return obj.toString()
}

@Composable
private fun RoutingSimulatorCard(repo: AppRepository) {
    var query by rememberSaveable { mutableStateOf("") }
    val settings = repo.settings
    val result = remember(query, settings.routingRulesJson, settings.routingMode, settings.routeBlockAds, settings.ipv6Enabled) {
        if (query.isBlank()) null else repo.simulateRoute(query)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Aether.GlassStrong.copy(alpha = .25f))
            .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            trx("Why is a site failing?"),
            color = Aether.Ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            trx("Type the address — Marble walks every rule in order and names the winner."),
            color = Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall
        )
        TinyField("example.com or 1.2.3.4", query, Modifier.fillMaxWidth()) { query = it }
        result?.let { sim ->
            val (verdictLabel, verdictTone) = when (sim.verdict) {
                RoutingOutbound.PROXY -> "PROXY" to Aether.Cyan
                RoutingOutbound.DIRECT -> "DIRECT" to Aether.Emerald
                RoutingOutbound.BLOCK -> "BLOCKED" to Aether.Danger
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrismBadge(verdictLabel, verdictTone, strong = true)
                Spacer(Modifier.width(8.dp))
                Text(
                    trx(sim.verdictReason),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
            }
            sim.steps.forEach { step ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        when {
                            step.skipped -> "–"
                            step.matched == true -> "✓"
                            step.matched == null -> "?"
                            else -> "·"
                        },
                        color = when {
                            step.matched == true -> verdictTone
                            step.matched == null -> Aether.Amber
                            else -> Aether.InkFaint
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(16.dp)
                    )
                    Column {
                        Text(
                            trx(step.title),
                            color = if (step.matched == true) Aether.Ink else Aether.InkMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (step.detail.isNotBlank()) {
                            Text(
                                trx(step.detail),
                                color = Aether.InkFaint,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutingExpertSection(repo: AppRepository, s: AppSettings) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    CyberButton(
        label = if (expanded) "Hide advanced rules" else "Advanced rules & tags",
        color = Aether.InkMuted,
        modifier = Modifier.fillMaxWidth(),
        icon = HomeIcon.FILTER
    ) { expanded = !expanded }

    AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                trx("Domain matcher"),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(
                    "hybrid" to "Hybrid (Trie + Regex)",
                    "linear" to "Linear",
                    "mph" to "Minimal Perfect Hash (MPH)"
                ).forEach { (value, _) ->
                    CyberChoiceChip(
                        text = value,
                        selected = s.routeDomainMatcher == value,
                        color = Aether.Emerald
                    ) { repo.updateSettings(s.copy(routeDomainMatcher = value)) }
                }
            }

            TinyField("Ad-block GeoSite tag", s.routeAdsTag, Modifier.fillMaxWidth()) {
                repo.updateSettings(s.copy(routeAdsTag = it.trim()))
            }
            TinyField("GeoIP direct tags (comma separated)", s.routeGeoIpTags, Modifier.fillMaxWidth()) {
                repo.updateSettings(s.copy(routeGeoIpTags = it))
            }
            TinyField("GeoSite direct tags (comma separated)", s.routeGeoSiteTags, Modifier.fillMaxWidth()) {
                repo.updateSettings(s.copy(routeGeoSiteTags = it))
            }

            Text(
                trx("Direct & block text lists"),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall
            )
            TinyField("Direct domains", s.routeDirectDomains, Modifier.fillMaxWidth()) {
                repo.updateSettings(s.copy(routeDirectDomains = it))
            }
            TinyField("Direct IPs", s.routeDirectIps, Modifier.fillMaxWidth()) {
                repo.updateSettings(s.copy(routeDirectIps = it))
            }
            TinyField("Proxy domains", s.routeProxyDomains, Modifier.fillMaxWidth()) {
                repo.updateSettings(s.copy(routeProxyDomains = it))
            }
            TinyField("Blocked domains", s.routeBlockDomains, Modifier.fillMaxWidth()) {
                repo.updateSettings(s.copy(routeBlockDomains = it))
            }
            TinyField("Blocked IPs", s.routeBlockIps, Modifier.fillMaxWidth()) {
                repo.updateSettings(s.copy(routeBlockIps = it))
            }
        }
    }
}

@Composable
private fun SubscriptionSettings(repo: AppRepository) {
    // MARBLE_MANUAL_BUCKET_V122 — the Manual bucket is permanent, so the toggle is gone.
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
    ProbeMethod.TUNNEL -> "Real test"
    ProbeMethod.TCP -> "TCP ping"
    ProbeMethod.ICMP -> "ICMP ping"
    ProbeMethod.HTTP -> "HTTP ping"
    ProbeMethod.DNS -> "DNS ping"
}

private fun probeMethodDetail(method: ProbeMethod): String = when (method) {
    ProbeMethod.HYBRID -> "Recommended • fast gate + real HTTPS test"
    ProbeMethod.TUNNEL -> "Slowest, proves the route end to end"
    ProbeMethod.TCP -> "Fastest, TCP handshake to server address"
    ProbeMethod.ICMP -> "Classic ping, bypasses the proxy"
    ProbeMethod.HTTP -> "Direct HTTPS test, includes TLS time"
    ProbeMethod.DNS -> "DNS resolution time, fastest check"
}

private fun probeMethodShortLabel(method: ProbeMethod): String = when (method) {
    ProbeMethod.HYBRID -> "Smart"
    ProbeMethod.TUNNEL -> "Tunnel"
    ProbeMethod.TCP -> "TCP"
    ProbeMethod.ICMP -> "ICMP"
    ProbeMethod.HTTP -> "HTTP"
    ProbeMethod.DNS -> "DNS"
}

/**
 * MARBLE_ONE_PING_V121 — one ping setting for the whole product.
 *
 * Marble used to run several differently-configured probes behind buttons that all said "ping":
 * the Servers group menu forced TCP, the Home button ran its own tunnel ladder, and this page
 * quietly rewrote a TCP choice into a tunnel test. The same server therefore reported different
 * latencies depending on which button was pressed. There is now exactly one method, chosen here,
 * and every measurement in the app — the Home ping button, a subscription's ping entry, Ping all
 * and ranking — runs it.
 *
 * Smart ping is the default and the right answer for almost everyone. The engine's raw operating
 * numbers (samples, timeouts, batch size) are no longer standalone controls on this page: they
 * are engine defaults, exposed only under Expert mode for people who genuinely tune them.
 */
@Composable
private fun ProbeSettings(repo: AppRepository) {
    val s = repo.settings
    val method = s.probeMethod

    Text(
        trx("Used by the Home ping button, subscription ping and Ping all."),
        color = Aether.InkMuted,
        style = settingsBodyStyle()
    )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ProbeMethod.entries.forEach { candidate ->
            val selected = method == candidate
            val tone = when (candidate) {
                ProbeMethod.HYBRID -> Aether.Amethyst
                ProbeMethod.TUNNEL -> Aether.Emerald
                ProbeMethod.TCP -> Aether.Cyan
                ProbeMethod.ICMP -> Aether.Amber
                ProbeMethod.HTTP -> Aether.CyanBright
                ProbeMethod.DNS -> Aether.AmethystBright
            }
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Aether.Glass.copy(alpha = .42f))
                    .border(
                        1.dp,
                        if (selected) tone.copy(alpha = .58f) else Aether.GlassBorderSoft.copy(alpha = .5f),
                        shape
                    )
                    .kineticClickable(role = Role.Button, boundedShape = shape) {
                        repo.updateSettings(repo.settings.copy(probeMethod = candidate))
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            trx(probeMethodTitle(candidate)),
                            color = if (selected) tone else Aether.Ink,
                            style = settingsRowTitleStyle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (candidate == ProbeMethod.HYBRID) {
                            Text(
                                trx("Default"),
                                color = tone,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(ServersBadgeShape)
                                    .background(tone.copy(alpha = .13f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        trx(probeMethodDetail(candidate)),
                        color = Aether.InkFaint,
                        style = settingsBodyStyle(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (selected) tone else Aether.InkFaint.copy(alpha = .30f))
                )
            }
        }
    }

    if (method == ProbeMethod.ICMP) {
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
                trx("ICMP bypasses the proxy; only Smart or Real test proves the route."),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (method == ProbeMethod.DNS) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Aether.AmethystBright.copy(alpha = .08f))
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("i", color = Aether.AmethystBright, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(9.dp))
            Text(
                trx("DNS ping measures resolution time only — it does not test the server itself."),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

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

    // MARBLE_ONE_PING_V121 — the engine's raw operating numbers (samples per server, timeout per
    // try, servers per run) are no longer controls. They were standalone technical operators with
    // no right answer a user could know, and every combination of them produced a different
    // "ping" for the same server. They remain as tuned engine defaults in AppSettings.
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
    // MARBLE_PRODUCT_SIMPLE_V117 — a flat little pill: tint wash + tinted ink when selected,
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
    // MARBLE_PRODUCT_SIMPLE_V117 — a flat two-line segment tile for the few-per-row choices
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
    // MARBLE_PRODUCT_SIMPLE_V117 — a Marble-style plain row: no box, no dot, no border. Just
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
