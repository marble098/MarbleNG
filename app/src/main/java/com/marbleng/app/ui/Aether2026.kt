@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.marbleng.app.ui

// Compatibility baseline retained for CI: Marble Product UI v9.1.0

// MARBLE_M3_EXPRESSIVE_UI_V53
// MARBLE_PRISM_UI_V54
// MARBLE_REAL_DEVICE_POLISH_V55

import android.Manifest
import android.content.Intent
import com.marbleng.app.BuildConfig
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.text.input.ImeAction
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

import com.marbleng.app.core.GeoAssetRegistry
import com.marbleng.app.core.IranPrecisionPack
import com.marbleng.app.core.RoutingEngine
import com.marbleng.app.core.RoutingPresets
import com.marbleng.app.core.BugSeverity
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.CoreEngineInfo
import com.marbleng.app.core.availableOn
import com.marbleng.app.core.coreEngine
import com.marbleng.app.core.unavailableReason
import com.marbleng.app.core.parseCoreEngine
import com.marbleng.app.core.IpFamilyScan
import com.marbleng.app.core.IpFamilySummary
import com.marbleng.app.core.IpFamilyVerdict
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
import com.marbleng.app.core.AutoServerSelector
import com.marbleng.app.core.AutoServerWeights
import com.marbleng.app.core.ServerCandidate
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
import kotlin.math.roundToLong
import kotlin.math.sin

private enum class SpatialTab(val label: String) {
    DECK("Home"),
    LIBRARY("Servers"),
    SETTINGS("Settings"),

    CUSTOM("Custom")
}

@Composable
private fun spatialTabLabel(tab: SpatialTab, customCaption: String = ""): String = when (tab) {
    SpatialTab.DECK -> Tr.now.tabHome
    SpatialTab.LIBRARY -> Tr.now.tabLibrary
    SpatialTab.SETTINGS -> Tr.now.tabSettings
    SpatialTab.CUSTOM -> customCaption.ifBlank { trx(tab.label) }
}

private fun rememberedSpatialTab(name: String): SpatialTab =
    runCatching { SpatialTab.valueOf(name) }.getOrDefault(SpatialTab.DECK)

private data class DockSlotChrome(
    val enabled: Boolean,
    val caption: String,
    val icon: DockSlotIcon,
    val accent: Color,
    val showStatusBadge: Boolean,
    val statusTone: Color,
    val statusDescription: String
)

@Composable
private fun dockSlotAccentTone(accent: DockSlotAccent): Color = when (accent) {
    DockSlotAccent.OCEAN -> Aether.Cyan
    DockSlotAccent.MINT -> Aether.Emerald

    DockSlotAccent.VIOLET -> if (homeCloudDark()) Color(0xFFB08CFF) else Color(0xFF7A5CD6)
    DockSlotAccent.AMBER -> Aether.Amber
}

@Composable
private fun dockSlotStateTone(state: String): Color = when (state.trim().uppercase()) {
    "CONNECTED" -> Aether.Emerald
    "CONNECTING" -> Aether.CyanBright
    "DISCONNECTING" -> Aether.Amber
    "BLOCKED" -> Aether.Danger
    else -> Aether.InkFaint
}

private class DockSlotTarget(
    val kind: DockSlotKind,
    val id: String,
    val sourceId: String,
    val displayName: String,
    val profiles: List<ProxyProfile>
) {
    val isEmpty: Boolean get() = kind != DockSlotKind.PULSE && profiles.isEmpty()
}

private const val DOCK_SLOT_ALL_SOURCES = "All servers"

@Composable
private fun dockSlotCaptionText(kind: DockSlotKind, targetName: String): String {
    val resolved = dockSlotDefaultCaption(kind, targetName)
    val isProductConstant =
        resolved == DOCK_SLOT_CAPTION_PULSE || resolved == DOCK_SLOT_CAPTION_FALLBACK
    return if (isProductConstant) trx(resolved) else resolved
}

private const val DOCK_SLOT_PICKER_CAP = 60

private fun dockSlotTarget(repo: AppRepository, kind: DockSlotKind): DockSlotTarget {
    val settings = repo.settings
    val library = repo.libraryProfiles
    return when (kind) {
        DockSlotKind.PULSE -> DockSlotTarget(
            kind = kind,
            id = "pulse",
            sourceId = "all",
            displayName = "",
            profiles = library
        )

        DockSlotKind.SOURCE -> {
            val id = settings.dockSlotSourceId.trim().ifBlank { "all" }
            val name = when (id) {
                "all" -> DOCK_SLOT_ALL_SOURCES
                "manual" -> "Manual"
                else -> repo.subscriptions.firstOrNull { it.id == id }?.name.orEmpty()
            }
            DockSlotTarget(
                kind = kind,
                id = id,
                sourceId = id,
                displayName = name,
                profiles = if (id == "all") library else library.filter { it.subscriptionId == id }
            )
        }

        DockSlotKind.CONFIG -> {
            val pinned = repo.profile(
                settings.dockSlotProfileId,
                settings.dockSlotProfileSourceId.takeIf { it.isNotBlank() }
            )
            DockSlotTarget(
                kind = kind,
                id = pinned?.id.orEmpty(),
                sourceId = pinned?.subscriptionId.orEmpty(),
                displayName = pinned?.let { stripLeadingFlag(it.name) }.orEmpty(),
                profiles = listOfNotNull(pinned)
            )
        }
    }
}

private data class InstalledApp(val label: String, val packageName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Aether2026App(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit = {}
) {
    val tabs = dockSlots(SpatialTab.entries, SpatialTab.CUSTOM, repo.settings.dockSlotEnabled)
    val initialIndex = dockSlotIndex(tabs, rememberedSpatialTab(repo.lastAppTab))
    val pagerState = rememberPagerState(initialPage = initialIndex) { tabs.size }
    var dialog by remember { mutableStateOf<String?>(null) }
    var settingsFocus by remember { mutableStateOf<String?>(null) }
    var detailProfile by remember { mutableStateOf<ProxyProfile?>(null) }
    var ipDetailsOpen by remember { mutableStateOf(false) }

    val detailBackProgress = remember { MutableStateFlow(0f) }
    val detailBack by detailBackProgress.collectAsState()
    PredictiveBackHandler(enabled = detailProfile != null) { events ->
        try {
            events.collect { event -> detailBackProgress.value = event.progress }
            detailProfile = null
        } catch (cancelled: CancellationException) {
        } finally {
            detailBackProgress.value = 0f
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pageOf: (SpatialTab) -> Int = { tab -> tabs.indexOf(tab).coerceAtLeast(0) }

    val goToTab: (Int) -> Unit = { page ->
        if (page != pagerState.currentPage) {
            scope.launch { pagerState.animateScrollToPage(page) }
        }
    }

    val currentTab = tabs.getOrElse(pagerState.currentPage) { tabs.first() }

    LaunchedEffect(tabs.size) {
        if (pagerState.currentPage > tabs.lastIndex) pagerState.scrollToPage(tabs.lastIndex)
    }

    LaunchedEffect(currentTab) {
        onContentScrollChanged(false)
        repo.rememberAppTab(currentTab.name)
    }

    val reportContentScroll: (Boolean) -> Unit = onContentScrollChanged

    LaunchedEffect(settingsFocus) {
        if (
            settingsFocus == "Routing" ||
            settingsFocus == SettingsPages.DOCK_SLOT ||
            settingsFocus == SettingsPages.TESTS
        ) {
            goToTab(pageOf(SpatialTab.SETTINGS))
        }
    }

    // MARBLE_HOME_NO_TRANSIENT_STATUS_V209 — Home deliberately has no short-lived status strip.

    LaunchedEffect(repo.message, repo.busy) {
        val shown = repo.message
        if (shown.isBlank() || repo.busy) return@LaunchedEffect
        delay(MarbleFeedbackPolicy.dwellMillis(actionRequired = false))
        if (repo.message == shown) repo.clearMessage()
    }

    val deck = rememberDeckEvidence(repo)
    val deckCopyIp = rememberCopyIpAction(repo, deck.evidence.ip)

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
            val ev = deck.evidence
            when (ev.routeState.connectVerb()) {
                MarbleConnectVerb.WAIT -> Unit
                MarbleConnectVerb.ADD_ROUTE -> goToTab(pageOf(SpatialTab.LIBRARY))
                MarbleConnectVerb.CONNECT -> repo.reconnectLastOrAuto(onConnect)

                MarbleConnectVerb.CANCEL,
                MarbleConnectVerb.DISCONNECT,
                MarbleConnectVerb.RESET -> repo.stopVpn()
            }
        },
        onCopyIp = deckCopyIp,
        onRefreshIp = { repo.refreshServerIntel(deck.profile, force = true) },
        onIpDetails = { ipDetailsOpen = true },

        onTestPing = {
            repo.measureHomePing()
        },
        onLibrary = { goToTab(pageOf(SpatialTab.LIBRARY)) },
        onConnectProfile = { profile -> onConnect(profile) },

        onAddRoute = { goToTab(pageOf(SpatialTab.LIBRARY)) },
        onRank = { repo.smartRank() },
        onPrivacy = {
            repo.audit()
            dialog = "Privacy"
        },
        onRouting = {
            settingsFocus = "Routing"
            goToTab(pageOf(SpatialTab.SETTINGS))
        },

        onTests = {
            settingsFocus = SettingsPages.TESTS
            goToTab(pageOf(SpatialTab.SETTINGS))
        },
        onPasteImport = {
            val pasted = deckClipboard.getText()?.text.orEmpty()
            if (pasted.isBlank()) {
                repo.setRuntimeMessage("Clipboard is empty")
            } else {
                repo.importClipboard(pasted, libraryIntakeTarget(repo))
            }
        },
        onQrImport = { deckQrSourceOpen = true },

        onPingGroup = {
            repo.pingHomeGroup()
        },

        onPingRoute = {
            repo.measureHomePing()
        },

        onPingLibrary = {
            repo.testAll()
        }
    )

    val slotKind = parseDockSlotKind(repo.settings.dockSlotKind)
    val slotTarget = dockSlotTarget(repo, slotKind)
    val slotStatusTone = dockSlotStateTone(repo.state)
    val slotChrome = DockSlotChrome(
        enabled = repo.settings.dockSlotEnabled,
        caption = dockSlotCaption(repo.settings.dockSlotLabel).ifBlank {
            dockSlotCaptionText(slotKind, slotTarget.displayName)
        },
        icon = parseDockSlotIcon(repo.settings.dockSlotIcon),
        accent = dockSlotAccentTone(parseDockSlotAccent(repo.settings.dockSlotAccent)),
        showStatusBadge = repo.settings.dockSlotShowStatusBadge,
        statusTone = slotStatusTone,
        statusDescription = homeStatusText(deck.evidence)
    )

    CompositionLocalProvider(
        LocalDockMetrics provides DockMetrics.of(repo.settings),

        LocalMarbleAmbientField provides repo.settings.homeAmbientBackdrop
    ) {
    // MARBLE_EDGE_TO_EDGE_BACKDROP_V190 — the page gradient is painted under the WHOLE window,

    Box(Modifier.fillMaxSize()) {
    DeepSpaceBackdrop(
        Modifier.matchParentSize(),
        flavor = homeFlavorFor(parseHomeStyle(repo.settings.homeStyle))
    )
    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // marble-page-transition-fast — MARBLE_STABLE_NAVIGATION_V202 uses the pager's native

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                beyondViewportPageCount = 0
            ) { pageIndex ->
                val reportThisPageScroll: (Boolean) -> Unit = { scrolling ->

                    if (pageIndex == pagerState.currentPage) reportContentScroll(scrolling)
                }
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
                        when (tabs.getOrNull(pageIndex) ?: SpatialTab.DECK) {
                    SpatialTab.DECK -> CyberDeck(
                        repo = repo,
                        deck = deck,
                        actions = deckActions,
                        onContentScrollChanged = reportThisPageScroll
                    )
                    SpatialTab.LIBRARY -> CyberLibrary(
                        repo = repo,
                        onConnect = onConnect,
                        onImportFile = onImportFile,
                        onDetails = { detailProfile = it },
                        onContentScrollChanged = reportThisPageScroll
                    )
                    SpatialTab.SETTINGS -> SpatialSettings(
                        repo = repo,
                        onDialog = { dialog = it },
                        focusSection = settingsFocus,
                        onContentScrollChanged = reportThisPageScroll
                    )

                    SpatialTab.CUSTOM -> CustomDockPage(
                        repo = repo,
                        deck = deck,
                        actions = deckActions,
                        target = slotTarget,
                        chrome = slotChrome,
                        onConnect = onConnect,
                        onDetails = { detailProfile = it },
                        onDialog = { dialog = it },
                        onCustomize = { settingsFocus = SettingsPages.DOCK_SLOT },
                        onContentScrollChanged = reportThisPageScroll
                    )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = detailProfile,
                modifier = Modifier
                    .matchParentSize()
                    .marblePredictiveBack(detailBack),
                transitionSpec = {
                    // MARBLE_STABLE_NAVIGATION_V202 — a detail page is a complete opaque surface,

                    expressiveFadeThrough()
                },
                label = "connection-detail-fade-through-v202"
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

            AnimatedVisibility(
                visible = repo.busy && !repo.inlineProgressActive,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat) +
                    slideInVertically(MarbleExpressiveSpecs.EntranceRiseSpatial) { height -> -height },
                exit = fadeOut(tween(MarbleExpressiveMotion.Short4, easing = MarbleExpressiveMotion.EmphasizedAccelerate)) +
                    slideOutVertically(MarbleExpressiveSpecs.RollOutSpatial) { height -> -height }
            ) {
                MarbleExpressiveLinearIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Aether.Cyan,
                    trackColor = Color.Transparent,
                    height = 4.dp
                )
            }

            // MARBLE_STABLE_NAVIGATION_V202 — the dock is an opaque floating object. Switching

            FloatingSpatialDock(
                selected = currentTab,
                slot = slotChrome,
                onSelect = { next ->
                    detailProfile = null
                    settingsFocus = null
                    goToTab(pageOf(next))
                }
            )

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
        }
    }
    }
    }

    if (ipDetailsOpen) {
        IpDetailsDialog(
            info = repo.serverIntel,
            loading = repo.serverIntelLoading,
            error = repo.serverIntelError,
            onDismiss = { ipDetailsOpen = false }
        )
    }

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

                            "Bug Finder" -> repo.bugFinderReportText()
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
            title = { Text(trx(what), color = Aether.Ink) },
            text = {
                SelectionContainer {
                    Text(
                        when (what) {
                            "Privacy" -> {
                                val report = repo.privacy
                                when {
                                    repo.state != "CONNECTED" ->
                                        "Connect first — the audit runs through the active proxy path."
                                    repo.busy && report == null ->
                                        "Running IPv4/IPv6 privacy audit through the active proxy route…"
                                    report == null ->
                                        "No privacy report yet — tap Privacy once the tunnel is healthy."
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
                                        append("\n\nIPv6 PROXY EXIT\n")
                                        append(report.proxyIpv6.ifBlank {
                                            if (repo.settings.ipv6Enabled) "unverified / exit may lack IPv6"
                                            else "not requested (IPv6 disabled)"
                                        })
                                        append("\n\nIPv6 PHYSICAL (MANUAL COMPARISON)\n")
                                        append(report.underlayIpv6.ifBlank { "unavailable" })
                                        append("\n\nLOCATION\n")
                                        append(report.cloudflareLocation.ifBlank { "unknown" })
                                        append("\n\nDNS OBSERVATION\n")
                                        append(report.dnsServers.ifBlank { "inconclusive" })
                                        append("\n\nSENTINEL\n")
                                        append(when {
                                            !report.healthy -> "AUDIT NEEDS ATTENTION • ${repo.sentinel.coverage}"
                                            repo.sentinel.healthy -> "HEALTHY"
                                            else -> repo.sentinel.coverage
                                        })
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
                    MarbleExpressiveLinearIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Aether.Cyan,
                        trackColor = Color.Transparent,
                        height = 4.dp
                    )
                }
                if (info == null) {
                    Text(
                        trx(if (error.isBlank()) "Resolving the public IP…" else compactInAppMessage(error)),
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
                        fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.Bold,
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
                        AnimatedVisibility(
                            visible = notesScroll.maxValue > 0,
                            enter = fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat),
                            exit = fadeOut(tween(MarbleExpressiveMotion.Short4))
                        ) {
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
    Box(modifier) {
        PrismBackdrop(Modifier.matchParentSize(), flavor)
    }
}

internal data class DockMetrics(
    val size: DockSize = DockSize.MEDIUM,
    val showIcons: Boolean = true,
    val showLabels: Boolean = true
) {
    val barHeight: Dp
        get() = when (size) {
            DockSize.SMALL -> 52.dp
            DockSize.MEDIUM -> 64.dp
            DockSize.LARGE -> 76.dp
        }

    val iconSize: Dp
        get() = when (size) {
            DockSize.SMALL -> 18.dp
            DockSize.MEDIUM -> 24.dp
            DockSize.LARGE -> 29.dp
        }

    val corner: Dp
        get() = when (size) {
            DockSize.SMALL -> 26.dp
            DockSize.MEDIUM -> 30.dp
            DockSize.LARGE -> 34.dp
        }

    val innerPadding: Dp
        get() = when (size) {
            DockSize.SMALL -> 5.dp
            DockSize.MEDIUM -> 7.dp
            DockSize.LARGE -> 9.dp
        }

    val clearance: Dp get() = barHeight + 26.dp

    companion object {
        fun of(settings: AppSettings): DockMetrics = DockMetrics(
            size = parseDockSize(settings.dockSize),
            showIcons = dockShowsIcons(settings.dockShowIcons, settings.dockShowLabels),
            showLabels = dockShowsLabels(settings.dockShowIcons, settings.dockShowLabels)
        )
    }
}

internal val LocalDockMetrics = staticCompositionLocalOf { DockMetrics() }

@Composable
private fun dockClearance(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        LocalDockMetrics.current.clearance

@Composable
private fun FloatingSpatialDock(
    selected: SpatialTab,
    slot: DockSlotChrome,
    onSelect: (SpatialTab) -> Unit
) {
    val metrics = LocalDockMetrics.current
    // MARBLE_BOTTOM_DOCK_UNIFIED_FLOATING_V661 — unified floating navigation lineage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val barShape = RoundedCornerShape(metrics.corner)

        // MARBLE_STABLE_NAVIGATION_V202 — one opaque body is deliberately cheaper and more

        val chrome = rememberMarbleFloatChrome()
        val dockSurface = chrome.surface
        val dockElevation = 14.dp

        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(metrics.barHeight)
                .shadow(
                    elevation = dockElevation,
                    shape = barShape,

                    ambientColor = chrome.shadow.copy(
                        alpha = marbleFloatShadowPair(dockElevation.value).first
                    ),
                    spotColor = chrome.shadow.copy(
                        alpha = marbleFloatShadowPair(dockElevation.value).second
                    )
                )
                .clip(barShape)
                .background(dockSurface)
                .border(
                    1.dp,

                    chrome.border,
                    barShape
                )
                .padding(horizontal = 8.dp, vertical = metrics.innerPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpatialTab.entries.forEach { item ->

                if (item == SpatialTab.CUSTOM && !slot.enabled) return@forEach
                val active = item == selected
                val isCustomSlot = item == SpatialTab.CUSTOM
                val slotAccent = if (isCustomSlot) slot.accent else Aether.Cyan

                val glassShape = marbleNativePill()

                val dockDark = homeCloudDark()
                val pillFill = marblePillFill(
                    accent = slotAccent,
                    surface = dockSurface,
                    fillAlpha = if (dockDark) 0.24f else 0.36f
                )
                val pillRim = marblePillRim(
                    accent = slotAccent,
                    surface = dockSurface,
                    rimAlpha = if (dockDark) 0.42f else 0.60f
                )
                val inkTone by animateColorAsState(
                    targetValue = if (active) {
                        marbleReadableOn(slotAccent, pillFill, 4.5f)
                    } else {
                        marbleReadableOn(Aether.InkMuted, dockSurface, 4.5f)
                    },
                    animationSpec = MarbleMotionSpecs.DockColor,
                    label = "dock-tone-${item.name}"
                )

                val pillBg by animateColorAsState(
                    targetValue = if (active) pillFill else pillFill.copy(alpha = 0f),
                    animationSpec = MarbleMotionSpecs.DockColor,
                    label = "dock-pill-${item.name}"
                )
                val indicatorTone by animateColorAsState(
                    targetValue = if (active) pillRim else pillRim.copy(alpha = 0f),
                    animationSpec = MarbleMotionSpecs.DockColor,
                    label = "dock-indicator-${item.name}"
                )
                // MARBLE_STABLE_NAVIGATION_V202 — selection has one fill and one rim. The old

                val spokenTab = if (item == SpatialTab.CUSTOM) slot.caption else trx(item.label)
                val selectedState = trx("Selected")
                val notSelectedState = trx("Not selected")
                val tabDescription = "$spokenTab ${trx("tab") }"
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

                            contentDescription = tabDescription
                            stateDescription = when {
                                active && isCustomSlot && slot.showStatusBadge ->
                                    "$selectedState • ${slot.statusDescription}"
                                active -> selectedState
                                else -> notSelectedState
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (metrics.showIcons) {
                        // MARBLE_STABLE_NAVIGATION_V202 — a tab acknowledgement is colour, not

                        Box(
                            modifier = Modifier.size(metrics.iconSize),
                            contentAlignment = Alignment.Center
                        ) {
                            MarbleTabIcon(
                                tab = item,
                                color = inkTone,
                                active = active,
                                slotIcon = slot.icon,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isCustomSlot && slot.showStatusBadge) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(if (metrics.size == DockSize.SMALL) 7.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(slot.statusTone)
                                        .border(1.5.dp, dockSurface, CircleShape)
                                )
                            }
                        }
                    }
                    if (metrics.showIcons && metrics.showLabels) {
                        Spacer(Modifier.width(7.dp))
                    }
                    if (metrics.showLabels) {
                        Text(
                            spatialTabLabel(item, slot.caption),
                            color = inkTone,
                            style = when (metrics.size) {
                                DockSize.SMALL -> MaterialTheme.typography.labelSmall
                                DockSize.MEDIUM -> MaterialTheme.typography.labelMedium
                                DockSize.LARGE -> MaterialTheme.typography.labelLarge
                            },
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.01.sp
                        )
                    }
                    if (isCustomSlot && slot.showStatusBadge && metrics.showLabels && !metrics.showIcons) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(slot.statusTone)
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
    slotIcon: DockSlotIcon = DockSlotIcon.DEFAULT,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val w=size.width
        val h=size.height

        val stroke=if(active) 2.3f.dp.toPx() else 1.9f.dp.toPx()
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

            SpatialTab.CUSTOM -> when(slotIcon) {
                DockSlotIcon.PULSE -> {
                    val trace=Path().apply {
                        moveTo(w*.14f,h*.52f)
                        lineTo(w*.32f,h*.52f)
                        lineTo(w*.42f,h*.26f)
                        lineTo(w*.56f,h*.76f)
                        lineTo(w*.66f,h*.44f)
                        lineTo(w*.86f,h*.44f)
                    }
                    drawPath(trace,color,style=line)
                }

                DockSlotIcon.SPARK -> {
                    val bolt=Path().apply {
                        moveTo(w*.58f,h*.12f)
                        lineTo(w*.28f,h*.54f)
                        lineTo(w*.48f,h*.54f)
                        lineTo(w*.40f,h*.88f)
                        lineTo(w*.72f,h*.44f)
                        lineTo(w*.52f,h*.44f)
                        close()
                    }
                    drawPath(bolt,color,style=line)
                }

                DockSlotIcon.LAYERS -> {
                    listOf(.30f,.50f,.70f).forEachIndexed { index,y ->
                        val inset=if(index==1) .16f else .22f
                        drawLine(
                            color=color,
                            start=Offset(w*inset,h*y),
                            end=Offset(w*(1f-inset),h*y),
                            strokeWidth=stroke,
                            cap=StrokeCap.Round
                        )
                    }
                    drawCircle(color,radius=w*.055f,center=Offset(w*.5f,h*.50f))
                }

                DockSlotIcon.BEARING -> {
                    drawCircle(
                        color=color,
                        radius=w*.32f,
                        center=Offset(w*.5f,h*.5f),
                        style=Stroke(width=stroke,cap=StrokeCap.Round)
                    )

                    drawLine(color,Offset(w*.5f,h*.5f),Offset(w*.70f,h*.30f),stroke,StrokeCap.Round)
                    drawLine(color,Offset(w*.5f,h*.5f),Offset(w*.38f,h*.62f),stroke,StrokeCap.Round)
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
                // MARBLE_VISUAL_POLISH_V218 — the page headline takes its weight from the type
                // ramp (headlineLarge is Bold now); the old FontWeight.Black override asked for a
                // weight no bundled face ships, so the platform smeared a synthesised faux-bold.
                style = MaterialTheme.typography.headlineLarge,
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
    val settled by animateFloatAsState(
        targetValue = fraction?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = MarbleMotionSpecs.ProgressFloat,
        label = "live-progress-fill"
    )
    val motion = MarbleMotion.current

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
            val head = -.4f + motion.loop(1_150) * 1.4f

            val widthPhase = motion.loop(900)
            val segment = size.width * ExpressiveMath.wavyValue(widthPhase, .26f, .48f)
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

private enum class HomeIcon {
    BRAND, POWER, STOP, CANCEL, RESET, SHIELD, TUNNEL, ROUTE,
    PING, JITTER, QUALITY, NODES, VERIFIED, CHECK, MODE, BENCHMARK,
    RANK, LIBRARY, PRIVACY, ROUTING, NETWORK, SERVER, DOWNLOAD, UPLOAD,
    DETAILS, SPARK, STATUS, MORE, MENU,

    CLIPBOARD, INFO, PALETTE, GLOBE, CHEVRON, BACK, FONT,

    TRASH,

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

        val stroke = (m * .094f).coerceIn(1.5f, 3.8f)
        val fine = (stroke * .80f).coerceIn(1.3f, 2.9f)

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
                drawLine(color, Offset(w*.24f, h*.29f), Offset(w*.76f, h*.29f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.24f, h*.5f), Offset(w*.62f, h*.5f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.24f, h*.71f), Offset(w*.48f, h*.71f), stroke, StrokeCap.Round)
            }

            HomeIcon.FILTER -> {
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
                val outerArch = Path().apply {
                    moveTo(w * .18f, h * .80f)
                    lineTo(w * .18f, h * .48f)
                    cubicTo(w * .18f, h * .20f, w * .82f, h * .20f, w * .82f, h * .48f)
                    lineTo(w * .82f, h * .80f)
                }
                drawPath(outerArch, color, style = line)
                val innerArch = Path().apply {
                    moveTo(w * .34f, h * .80f)
                    lineTo(w * .34f, h * .54f)
                    cubicTo(w * .34f, h * .36f, w * .66f, h * .36f, w * .66f, h * .54f)
                    lineTo(w * .66f, h * .80f)
                }
                drawPath(innerArch, color, style = fineLine)
                drawLine(color, Offset(w * .10f, h * .80f), Offset(w * .90f, h * .80f), stroke, StrokeCap.Round)
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
                val pivotX = w * .40f
                val pivotY = h * .66f
                drawArc(color,205f,130f,false,Offset(pivotX-m*.14f,pivotY-m*.14f),Size(m*.28f,m*.28f),style=line)
                drawArc(color,205f,130f,false,Offset(pivotX-m*.26f,pivotY-m*.26f),Size(m*.52f,m*.52f),style=line)
                drawArc(color,205f,130f,false,Offset(pivotX-m*.37f,pivotY-m*.37f),Size(m*.74f,m*.74f),style=line)
                drawCircle(color, m*.085f, Offset(pivotX,pivotY))

                drawLine(color, Offset(w*.62f, h*.88f), Offset(w*.82f, h*.62f), stroke, StrokeCap.Round)
                drawCircle(color, (stroke * .62f).coerceAtLeast(1.2f), Offset(w*.62f, h*.88f))
            }

            HomeIcon.CAMERA -> {
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
                val roof = Path().apply {
                    moveTo(w * .16f, h * .48f)
                    lineTo(w * .50f, h * .20f)
                    lineTo(w * .84f, h * .48f)
                }
                drawPath(roof, color, style = line)
                val body = Path().apply {
                    moveTo(w * .24f, h * .44f)
                    lineTo(w * .24f, h * .78f)
                    quadraticBezierTo(w * .24f, h * .82f, w * .28f, h * .82f)
                    lineTo(w * .42f, h * .82f)
                    lineTo(w * .42f, h * .58f)
                    quadraticBezierTo(w * .42f, h * .54f, w * .50f, h * .54f)
                    quadraticBezierTo(w * .58f, h * .54f, w * .58f, h * .58f)
                    lineTo(w * .58f, h * .82f)
                    lineTo(w * .72f, h * .82f)
                    quadraticBezierTo(w * .76f, h * .82f, w * .76f, h * .78f)
                    lineTo(w * .76f, h * .44f)
                }
                drawPath(body, color, style = fineLine)
            }

            HomeIcon.BENCHMARK, HomeIcon.RANK -> {
                drawArc(
                    color = color,
                    startAngle = 145f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawCircle(color, m * .065f, Offset(w * .50f, h * .58f))
                val needle = Path().apply {
                    moveTo(w * .47f, h * .56f)
                    lineTo(w * .70f, h * .34f)
                    lineTo(w * .53f, h * .60f)
                    close()
                }
                drawPath(needle, color)
                drawLine(color, Offset(w * .48f, h * .58f), Offset(w * .72f, h * .32f), stroke, StrokeCap.Round)
            }

            HomeIcon.LIBRARY -> {
                drawOval(
                    color = color,
                    topLeft = Offset(w * .20f, h * .18f),
                    size = Size(w * .60f, h * .20f),
                    style = line
                )
                val mid = Path().apply {
                    moveTo(w * .20f, h * .44f)
                    cubicTo(w * .20f, h * .56f, w * .80f, h * .56f, w * .80f, h * .44f)
                }
                drawPath(mid, color, style = fineLine)
                val bottom = Path().apply {
                    moveTo(w * .20f, h * .28f)
                    lineTo(w * .20f, h * .70f)
                    cubicTo(w * .20f, h * .82f, w * .80f, h * .82f, w * .80f, h * .70f)
                    lineTo(w * .80f, h * .28f)
                }
                drawPath(bottom, color, style = fineLine)
            }

            HomeIcon.NETWORK -> {
                val xs = listOf(.24f,.42f,.60f,.78f)
                val tops = listOf(.66f,.54f,.40f,.25f)
                for (i in xs.indices) {
                    drawLine(color,Offset(w*xs[i],h*.75f),Offset(w*xs[i],h*tops[i]),stroke*1.35f,StrokeCap.Round)
                }
            }

            HomeIcon.SERVER -> {
                val r = m * .06f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * .18f, h * .22f),
                    size = Size(w * .64f, h * .24f),
                    cornerRadius = CornerRadius(r, r),
                    style = line
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * .18f, h * .54f),
                    size = Size(w * .64f, h * .24f),
                    cornerRadius = CornerRadius(r, r),
                    style = line
                )
                drawCircle(color, m * .035f, Offset(w * .30f, h * .34f))
                drawCircle(color, m * .035f, Offset(w * .30f, h * .66f))
                drawLine(color, Offset(w * .44f, h * .34f), Offset(w * .70f, h * .34f), fine, StrokeCap.Round)
                drawLine(color, Offset(w * .44f, h * .66f), Offset(w * .70f, h * .66f), fine, StrokeCap.Round)
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
                drawCircle(color, m * .14f, Offset(w * .50f, h * .50f), style = line)
                for (i in 0 until 6) {
                    val angle = (i * 60.0) * Math.PI / 180.0
                    val cos = Math.cos(angle).toFloat()
                    val sin = Math.sin(angle).toFloat()
                    val r1 = m * .22f
                    val r2 = m * .34f
                    drawLine(
                        color,
                        Offset(w * .50f + cos * r1, h * .50f + sin * r1),
                        Offset(w * .50f + cos * r2, h * .50f + sin * r2),
                        stroke * 1.5f,
                        StrokeCap.Round
                    )
                }
            }

            HomeIcon.MORE -> {
                drawCircle(color, m*.075f, Offset(w*.25f,h*.50f))
                drawCircle(color, m*.075f, Offset(w*.50f,h*.50f))
                drawCircle(color, m*.075f, Offset(w*.75f,h*.50f))
            }

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

            HomeIcon.PALETTE -> {
                val palette = Path().apply {
                    moveTo(w * .50f, h * .18f)
                    cubicTo(w * .22f, h * .18f, w * .16f, h * .46f, w * .22f, h * .66f)
                    cubicTo(w * .26f, h * .80f, w * .40f, h * .82f, w * .48f, h * .74f)
                    cubicTo(w * .54f, h * .68f, w * .58f, h * .64f, w * .66f, h * .66f)
                    cubicTo(w * .78f, h * .70f, w * .84f, h * .60f, w * .84f, h * .48f)
                    cubicTo(w * .84f, h * .28f, w * .70f, h * .18f, w * .50f, h * .18f)
                    close()
                }
                drawPath(palette, color, style = line)
                drawCircle(color, m * .045f, Offset(w * .36f, h * .36f))
                drawCircle(color, m * .045f, Offset(w * .52f, h * .30f))
                drawCircle(color, m * .045f, Offset(w * .68f, h * .36f))
                drawCircle(color, m * .055f, Offset(w * .68f, h * .54f), style = fineLine)
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
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .size(42.dp)
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
        HomeVectorIcon(icon, color, Modifier.size(22.dp))
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
        HomeVectorIcon(icon, tone, Modifier.size(15.dp))
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
    Row(
        modifier=modifier
            .fillMaxWidth()
            .heightIn(min=60.dp)
            .padding(vertical=5.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(11.dp)
    ) {
        if(title == "MarbleNG") {
            Box(
                modifier=Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Aether.Cyan,Aether.Amethyst)
                        )
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha=.22f),
                        RoundedCornerShape(13.dp)
                    ),
                contentAlignment=Alignment.Center
            ) {
                Icon(
                    painter=painterResource(R.drawable.ic_marble_prism),
                    contentDescription=null,
                    tint=Color.White,
                    modifier=Modifier.size(24.dp)
                )
            }
        } else {
            val icon=when(title) {
                "Servers" -> HomeIcon.LIBRARY
                "Settings" -> HomeIcon.MODE
                else -> HomeIcon.DETAILS
            }
            HomeIconTile(icon,Aether.Cyan,Modifier.size(40.dp))
        }

        Column(
            modifier=Modifier.weight(1f),
            verticalArrangement=Arrangement.spacedBy(1.dp)
        ) {
            Text(
                trx(title),
                color=Aether.Ink,

                style=MaterialTheme.typography.headlineSmall,
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

@Composable
private fun MarbleMenuPanel(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = open) { onClose() }

    val chrome = rememberMarbleFloatChrome()

    val panelShape = marbleNativeShape(MarbleNativeSpec.CardCornerDp.dp)
    val panelShadow = marbleNativeShadow(Aether.Cyan)
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
                    .padding(start = 16.dp, end = 16.dp, top = 60.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = MarbleNativeSpec.CardLiftedElevationDp.dp,
                        shape = panelShape,
                        ambientColor = chrome.shadow.copy(alpha = .26f),
                        spotColor = chrome.shadow.copy(alpha = .34f)
                    )
                    .shadow(
                        elevation = MarbleNativeSpec.ContactElevationDp.dp,
                        shape = panelShape,
                        clip = false,
                        ambientColor = panelShadow.contact,
                        spotColor = panelShadow.contact
                    )
                    .clip(panelShape)
                    .background(marbleNativeContainerHigh())
                    .border(1.dp, marbleNativeOutline(), panelShape),
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
            HomeVectorIcon(icon, tone, Modifier.size(20.dp))
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
                Modifier.size(19.dp)
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
internal fun NationalEventBanner(repo: AppRepository, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = repo.nationalEventCause.isNotEmpty(),
        modifier = modifier,

        enter = fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat) +
            slideInVertically(MarbleExpressiveSpecs.EntranceRiseSpatial) { height -> -height },
        exit = fadeOut(tween(MarbleExpressiveMotion.Short4, easing = MarbleExpressiveMotion.EmphasizedAccelerate)) +
            slideOutVertically(MarbleExpressiveSpecs.RollOutSpatial) { height -> -height }
    ) {
        val chrome = rememberMarbleFloatChrome()
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = chrome.shadow.copy(alpha = .16f),
                    spotColor = Aether.Danger.copy(alpha = .22f)
                )
                .clip(shape)
                .background(chrome.surface)
                .background(
                    Brush.verticalGradient(
                        listOf(Aether.Danger.copy(alpha = .12f), Color.Transparent)
                    )
                )
                .border(1.dp, Aether.Danger.copy(alpha = .38f), shape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🚩", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Text(
                "National filtering detected • ranking frozen • ${repo.nationalEventCause.replace("-", " ")}",
                color = marbleReadableOn(Aether.Danger, chrome.surface, 4.5f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeMetricBento(repo: AppRepository) {
    val pingHistory = remember { mutableStateListOf<Int>() }

    val pingFlags = remember { mutableStateListOf<Boolean>() }

    LaunchedEffect(repo.livePingMs) {
        val value = repo.livePingMs
        if (value > 0) {
            if (pingHistory.lastOrNull() != value || pingHistory.size < 2) {
                pingHistory += value
                pingFlags += repo.homePingInjectedReset
                while (pingHistory.size > 36) {
                    pingHistory.removeAt(0)
                    pingFlags.removeAt(0)
                }
            }
        } else if (repo.state != "CONNECTED") {
            pingHistory.clear()
            pingFlags.clear()
        }
    }

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

                sparkline = pingHistory,
                flaggedSamples = pingFlags,

                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight()
                    .marbleStaggerIn(1)
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
                        .marbleStaggerIn(2)
                )
                MarbleMetricCard(
                    title = "Quality",
                    value = if (routeScore >= 0) routeScore.toString() else "—",
                    unit = if (routeScore >= 0) "%" else "",
                    tone = qualityTone,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .marbleStaggerIn(3)
                )
            }
        }
    }
}

@Composable
private fun marbleSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
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

    val shape=marbleNativeShape(MarbleNativeSpec.CardCornerDp.dp)
    val nativeShadow = marbleNativeShadow(tone)
    val tileFill = if (checked) {
        marbleNativeSelectedFill(Aether.Cyan)
    } else {
        marbleNativeCardFill()
    }

    Column(
        modifier=Modifier
            .fillMaxWidth()
            .heightIn(min=MarbleNativeSpec.RowTwoLineHeightDp.dp)
            .shadow(
                elevation = MarbleNativeSpec.ContactElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = nativeShadow.contact,
                spotColor = nativeShadow.contact
            )
            .clip(shape)
            .background(tileFill)
            .border(1.dp, marbleNativeOutline(), shape)
            .kineticClickable(
                enabled = enabled,
                role = Role.Switch,
                boundedShape = shape,
                showIndication = false
            ) { onChecked(!checked) }
            .padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier=Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(MarbleNativeSpec.LeadingContainerDp.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha=if (homeCloudDark()) .22f else .12f).compositeOver(tileFill)),
                contentAlignment=Alignment.Center
            ) {
                HomeVectorIcon(icon,tone,Modifier.size(MarbleNativeSpec.LeadingGlyphDp.dp))
            }
            Spacer(Modifier.weight(1f))
            MarbleNativeSwitch(
                checked=checked,
                onCheckedChange=onChecked,
                enabled=enabled
            )
        }
        Text(
            trx(title),
            color=Aether.Ink,
            style=MaterialTheme.typography.bodyLarge,
            fontWeight=FontWeight.Medium,
            maxLines=1
        )
    }
}

private class DeckEvidence(
    val profile: ProxyProfile?,
    val evidence: HomeEvidence
)

@Composable
private fun rememberDeckEvidence(repo: AppRepository): DeckEvidence {
    val active = repo.homeRoute()

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

    // MARBLE_PING_USER_TAPPED_ONLY_V143 — Home never measures ping in the background. The value

    val connectButtonStyle = parseConnectButtonStyle(repo.settings.connectButtonStyle)

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalConnectButtonStyle provides connectButtonStyle) {
            HomeStyleSurface(
                style = parseHomeStyle(repo.settings.homeStyle),
                evidence = evidence,
                actions = actions,
                bottomClearance = dockClearance(),

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
            (fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat) +
                slideInVertically(MarbleExpressiveSpecs.RollInSpatial) { it / 3 }) togetherWith
                (fadeOut(tween(MarbleExpressiveMotion.Short4, easing = MarbleExpressiveMotion.EmphasizedAccelerate)) +
                    slideOutVertically(MarbleExpressiveSpecs.RollOutSpatial) { -it / 3 })
        },
        label="home-status-title-anchor-v186",
        modifier=modifier
            .fillMaxWidth()
            .heightIn(min=titleBlock)
    ) { text ->
        Text(
            trx(text),
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
    val shape=marbleNativeShape(MarbleNativeSpec.CardCornerDp.dp)
    Row(
        modifier
            .heightIn(min=78.dp)
            .prismElevated(
                shape=shape,
                tone=color,
                fill=marbleNativeCardFill(),
                tint=Brush.linearGradient(
                    listOf(
                        color.copy(alpha=.075f),
                        Color.Transparent
                    )
                )
            )

            .kineticClickable(
                role=Role.Button,
                pressScale=.96f,
                releaseSpec=MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick=onClick
            )
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
            HomeVectorIcon(icon,color,Modifier.size(22.dp))
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
            Modifier.size(18.dp)
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
                    Modifier.size(24.dp)
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
                        !repo.settings.ipv6Enabled -> "IPv6 captured and blocked inside VPN"
                        repo.networkSnapshot.hasIpv6 -> "IPv6 endpoint available on this network"
                        else -> "IPv6 sites may work through an IPv4 proxy exit"
                    },
                    checked=repo.settings.ipv6Enabled,
                    enabled=!repo.busy
                ) { enabled ->

                    repo.updateSettings(
                        repo.settings.copy(
                            addressFamilyMode = if (enabled) AddressFamilyMode.SMART else AddressFamilyMode.FORCE_IPV4,
                            ipv6Enabled = enabled,
                            preferIpv6 = enabled,
                            dnsQueryStrategy = if (enabled) "UseIP" else "UseIPv4"
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

    val motion = MarbleMotion.current
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
                .graphicsLayer {
                    alpha = if (scanning) .76f + motion.breathe(2_100) * .24f else 1f
                }
                .clip(RoundedCornerShape(13.dp))
                .background(tone.copy(alpha = .11f)),
            contentAlignment = Alignment.Center
        ) {
            HomeVectorIcon(if (scanning) HomeIcon.BENCHMARK else HomeIcon.SHIELD, tone, Modifier.size(22.dp))
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

    val motion = MarbleMotion.current

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
                    .kineticClickable(
                        role = Role.Button,
                        pressScale = .95f,
                        releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize().padding(8.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(statusColor.copy(alpha = .08f), r)
                    if (connecting) {
                        val orbPhase = motion.loop(1_300)
                        drawArc(
                            color = statusColor,
                            startAngle = -90f + orbPhase * 360f,
                            sweepAngle = ExpressiveMath.arcSweep(orbPhase, 150f, 280f),
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
                    fontWeight = FontWeight.Bold,

                    modifier = Modifier.marblePopWhen(statusTitle, peak = 1.28f)
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
            icon?.let { HomeVectorIcon(it, valueColor, Modifier.size(14.dp)) }
            Text(
                trx(label).uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            MarbleExpressiveValueText(
                value = value,
                color = valueColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(trx(unit), color = valueColor.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
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
    val shape = RoundedCornerShape(22.dp)
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

            .kineticClickable(
                role = Role.Button,
                pressScale = .96f,
                boundedShape = shape,
                releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick = onClick
            )
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

private enum class LibraryGroupKind { SUBSCRIPTION, MANUAL, COUNTRY }

private data class LibraryGroup(
    val key: String,
    val title: String,
    val kind: LibraryGroupKind,
    val flag: String = "",
    val profiles: List<ProxyProfile>
)

private data class AddNodeProtocol(
    val label: String,
    val target: ManualProtocol?,
    val note: String = ""
)

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

private val ServersCardShape = RoundedCornerShape(20.dp)
private val ServersMenuShape = RoundedCornerShape(16.dp)
private val ServersBadgeShape = RoundedCornerShape(8.dp)
private val ServersPillShape = RoundedCornerShape(999.dp)

// MARBLE_SERVERS_HIERARCHY_V189 — the two levels of the page now own two different silhouettes.
// MARBLE_VISUAL_POLISH_V218 — the group's head silhouette is no longer a static shape: the
// header morphs its own bottom corners on the animated attach fraction (see ServersGroupHeader),
// so the card closes through every radius between the two states instead of jumping between them.

private val ServersGroupTailShape = RoundedCornerShape(
    bottomStart = ServersHierarchy.GROUP_CORNER_DP.dp,
    bottomEnd = ServersHierarchy.GROUP_CORNER_DP.dp
)
private val ServersGroupBodyShape = RoundedCornerShape(0.dp)

private val ServersListHeadShape = RoundedCornerShape(
    topStart = ServersHierarchy.LIST_CORNER_DP.dp,
    topEnd = ServersHierarchy.LIST_CORNER_DP.dp
)
private val ServersListTailShape = RoundedCornerShape(
    bottomStart = ServersHierarchy.LIST_CORNER_DP.dp,
    bottomEnd = ServersHierarchy.LIST_CORNER_DP.dp
)
private val ServersListBodyShape = RoundedCornerShape(0.dp)

/**
 * One height for every control of the filter rail — capsules and icon buttons alike.
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — the rail was 38 dp of pill with 7 dp between four controls; it
 * is 32 dp with 6 dp now, which is what lets both capsules carry a count badge without pushing
 * the row past the edge of a phone.
 */
private val ServersRailControlSize = 32.dp

@Composable
private fun serversGroupFrameColor(): Color = marbleNativeOutline()

private val ServersGroupFrameWidth = ServersHierarchy.GROUP_BORDER_DP.dp

@Composable
private fun serversRowDividerColor(): Color =
    marbleNativeOutline().copy(alpha = ServersHierarchy.ROW_DIVIDER_ALPHA)

private fun Modifier.serversStackedFrame(
    openTop: Boolean = false,
    openBottom: Boolean = false,
    openBottomFraction: Float = if (openBottom) 1f else 0f,
    color: Color,
    width: Dp = 1.dp,
    radius: Dp = 16.dp
): Modifier = drawBehind {
    val stroke = width.toPx()
    val r = radius.toPx()
    val top = if (openTop) -r * 2f else 0f
    // MARBLE_VISUAL_POLISH_V218 — how far the bottom edge stays hidden is a fraction, not a
    // switch: closing a sub bends the outline's bottom corners back into view in step with the
    // card's own morphing clip instead of snapping them in on the collapse's first frame.
    val bottom = size.height + ServersHierarchy.frameBottomExtension(openBottomFraction, r)

    clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2f, top + stroke / 2f),
            size = Size(size.width - stroke, bottom - top - stroke),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = stroke)
        )
    }
}

private fun libraryIntakeTarget(@Suppress("UNUSED_PARAMETER") repo: AppRepository): String = "manual"

/**
 * Subscription accounting, straight from the provider's subscription-userinfo headers.
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — the sizing, the percent and the colour tier are pure functions
 * in [ServersHierarchy] (one `compactBytes`, not a second formatter here), so the bar, its label
 * and its colour are three readings of one number.
 */
private fun subscriptionUsageText(sub: Subscription): String =
    ServersHierarchy.usageText(sub.uploadBytes + sub.downloadBytes, sub.totalBytes)

private fun subscriptionUsedBytes(sub: Subscription): Long =
    (sub.uploadBytes + sub.downloadBytes).coerceAtLeast(0L)

private fun subscriptionUsageFraction(sub: Subscription): Float =
    ServersHierarchy.usageFraction(subscriptionUsedBytes(sub), sub.totalBytes)

private fun subscriptionUsageTier(sub: Subscription): SubscriptionUsageTier =
    ServersHierarchy.usageTier(subscriptionUsedBytes(sub), sub.totalBytes)

@Composable
private fun usageTierTone(tier: SubscriptionUsageTier): Color = when (tier) {
    SubscriptionUsageTier.CALM -> Aether.Emerald
    SubscriptionUsageTier.WATCH -> Aether.Amber
    SubscriptionUsageTier.CRITICAL -> Aether.Danger
    SubscriptionUsageTier.UNKNOWN -> Aether.InkFaint
}

@Composable
private fun subscriptionExpiryText(sub: Subscription): String {
    if (sub.expireAt <= 0L) return trx("Unlimited")
    val formatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
    val expired = sub.expireAt < System.currentTimeMillis()
    return if (expired) trx("Expired") else formatter.format(java.util.Date(sub.expireAt))
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

    val entranceArmed = rememberMarbleEntranceWindow()
    val settings = repo.settings

    var search by rememberSaveable { mutableStateOf("") }

    var addMenuOpen by remember { mutableStateOf(false) }
    var addForm by remember { mutableStateOf<String?>(null) }
    val qrImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) repo.importQrImage(uri, libraryIntakeTarget(repo))
    }

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

    var pruneFailedTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var manualBucketOpen by remember { mutableStateOf(false) }
    var pruneEverywhere by remember { mutableStateOf(false) }

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

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text(trx("Edit server"), color = Aether.Ink) },
            text = {
                val renameIme = rememberMarbleImeHider()
                MarbleImeReleaseOnDispose(renameIme)
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(trx("Display name")) },
                    singleLine = true,
                    shape = ServersCardShape,
                    keyboardOptions = marbleImeOptions(ImeAction.Done),
                    keyboardActions = marbleImeActions(renameIme),
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
                pruneFailedTarget = target.id to kind
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
        val targetId = request.first
        val kind = request.second
        val failedCount = repo.failedSubscriptionNodeCount(targetId, kind)
        val scopeName = repo.libraryScopeLabel(targetId)
        AlertDialog(
            onDismissRequest = { pruneFailedTarget = null },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text(trx("Remove failed $kind servers?"), color = Aether.Danger) },
            text = {
                Text(
                    "This removes $failedCount failed server" +
                        (if (failedCount == 1) "" else "s") + " from $scopeName. " +
                        trx("Servers that were never measured are kept."),
                    color = Aether.InkMuted
                )
            },
            confirmButton = {
                MarbleDialogAction(
                    label = "Remove failed",
                    tone = Aether.Danger,
                    enabled = !repo.busy && failedCount > 0 && repo.state == "DISCONNECTED",
                    onClick = {
                        repo.removeFailedSubscriptionNodes(targetId, kind)
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

    if (manualBucketOpen) {
        ManualBucketCleanupDialog(
            repo = repo,
            onPrune = { kind ->
                pruneFailedTarget = "manual" to kind
                manualBucketOpen = false
            },
            onDismiss = { manualBucketOpen = false }
        )
    }

    if (pruneEverywhere) {
        val failedCount = repo.failedNodeCountEverywhere()
        AlertDialog(
            onDismissRequest = { pruneEverywhere = false },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text(trx("Remove failed servers everywhere?"), color = Aether.Danger) },
            text = {
                Text(
                    trx("This removes") + " $failedCount " + trx("failed server") +
                        (if (failedCount == 1) "" else "s") + " " +
                        trx("from every group, including Manual.") + " " +
                        trx("Servers that were never measured are kept."),
                    color = Aether.InkMuted
                )
            },
            confirmButton = {
                MarbleDialogAction(
                    label = "Remove failed",
                    tone = Aether.Danger,
                    enabled = !repo.busy && failedCount > 0 && repo.state == "DISCONNECTED",
                    onClick = {
                        repo.removeFailedSubscriptionNodes("all", "SMART")
                        repo.removeFailedSubscriptionNodes("all", "TUNNEL")
                        pruneEverywhere = false
                    }
                )
            },
            dismissButton = {
                MarbleDialogAction(
                    label = "Cancel",
                    tone = Aether.InkMuted,
                    onClick = { pruneEverywhere = false }
                )
            }
        )
    }

    deleteSubscription?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteSubscription = null },
            containerColor = Aether.VoidElevated,
            shape = ServersCardShape,
            title = { Text(trx("Delete ${target.name}?"), color = Aether.Danger) },
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

    repo.ipFamilyScanResult?.let { scan ->
        IpFamilyScanDialog(scan = scan, onDismiss = { repo.dismissIpFamilyScanResult() })
    }
    repo.ipFamilyScanSummary?.let { summary ->
        IpFamilyGroupDialog(summary = summary, onDismiss = { repo.dismissIpFamilyScanResult() })
    }

    val tileLayout = settings.serversLayoutEnum == ServerLayout.GRID
    val tileColumns = rememberServerTileColumns()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = dockClearance() + 28.dp
        )

    ) {
        item(key = "servers-header") {
            ServersTopBar(
                addOpen = addMenuOpen,
                onAdd = { addMenuOpen = !addMenuOpen },
                onAddDismiss = { addMenuOpen = false },
                onAddAction = { action ->
                    when (action) {
                        ServersAddAction.CLIPBOARD -> {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            if (pasted.isBlank()) {
                                addForm = "node"
                            } else {
                                val intake = libraryIntakeTarget(repo)
                                val addedId = repo.importClipboard(pasted, intake)
                                repo.selectLibrarySource(addedId ?: intake)
                            }
                        }

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
                // MARBLE_SERVERS_HIERARCHY_V189 — the counts the header line used to carry on its

                groupBadge = ServersHierarchy.groupBadge(groups.size),
                serverBadge = "${visibleProfiles.size}",
                serverBadgeSpoken = ServersHierarchy.serverBadge(visibleProfiles.size),
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
                        ServersAdvancedAction.PRUNE_FAILED_ALL -> pruneEverywhere = true
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

        groups.forEachIndexed { groupIndex, group ->
            val collapsed = group.key in repo.libraryCollapsedSources
            val subscription = repo.subscriptions.firstOrNull { it.id == group.key }
            val total = when (group.kind) {
                LibraryGroupKind.SUBSCRIPTION -> repo.subscriptionNodeCount(group.key)
                else -> group.profiles.size
            }

            val stacked = !collapsed && group.profiles.isNotEmpty()

            item(key = "group-${group.key}") {
                ServersGroupHeader(
                    group = group,
                    modifier = Modifier.marbleStaggerIn(groupIndex, enabled = entranceArmed()),
                    attachedBelow = stacked,
                    subscription = subscription,
                    collapsed = collapsed,
                    shown = group.profiles.size,
                    total = total,
                    refreshing = group.key in repo.refreshingSources,

                    pinging = repo.probeActive && group.profiles.any { it.id in repo.probeBatch },
                    cancelling = repo.probeCancelling,
                    autoRefresh = settings.subscriptionAutoRefresh,
                    onToggle = { repo.setLibrarySourceCollapsed(group.key, !collapsed) },
                    onRefresh = { repo.refresh(group.key) },

                    onPing = {
                        if (group.kind == LibraryGroupKind.COUNTRY) {
                            repo.pingProfiles(group.profiles, group.title, group.key)
                        } else {
                            repo.testSource(group.key)
                        }
                    },
                    onCancelPing = { repo.cancelProbes() },
                    onWebsite = { url -> openExternal(context, url) },
                    onMenu = {
                        when (it) {
                            ServersGroupAction.MANAGE -> {
                                if (subscription == null) {
                                    manualBucketOpen = true
                                } else {
                                    manageSubscription = subscription
                                    editSubscriptionName = subscription.name
                                    editSubscriptionUrl = subscription.url
                                }
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
                            ServersGroupAction.SHOW_ONLY -> repo.selectLibrarySource(group.key)
                            ServersGroupAction.SHOW_ALL -> repo.selectLibrarySource("all")
                            ServersGroupAction.SCAN_FAMILY ->
                                repo.scanIpFamilyForProfiles(group.profiles, group.title)
                            ServersGroupAction.DELETE -> deleteSubscription = subscription
                        }
                    }
                )
            }

            if (tileLayout && !collapsed) {
                val tileRows = ServerTilePolicy.chunkRows(group.profiles, tileColumns)
                itemsIndexed(
                    items = tileRows,
                    key = { rowIndex, row -> "${group.key}:tiles:${rowIndex}:${row.first().id}" }
                ) { rowIndex, row ->
                    val lastTileRow = rowIndex == tileRows.lastIndex
                    Box(
                        Modifier
                            .animateItem()
                            .marbleStaggerIn(rowIndex + 1, enabled = entranceArmed() && rowIndex < 6)
                            .fillMaxWidth()
                            .clip(if (lastTileRow) ServersGroupTailShape else ServersGroupBodyShape)
                            .background(marbleNativeCardFill())
                            .serversStackedFrame(
                                openTop = true,
                                openBottom = !lastTileRow,
                                color = serversGroupFrameColor(),
                                width = ServersGroupFrameWidth,
                                radius = ServersHierarchy.GROUP_CORNER_DP.dp
                            )
                    ) {
                        ServerTileRow(
                            profiles = row,
                            columns = tileColumns,
                            modifier = Modifier.padding(
                                horizontal = ServersHierarchy.LIST_INSET_DP.dp,
                                vertical = 6.dp
                            )
                        ) { profile ->
                            val location = repo.serverLocation(profile)

                            val familyScan = repo.ipFamilyScan(profile)
                            ServerTile(
                                profile = profile,
                                result = benchmarks[profile.id],
                                selected = repo.isSelectedProfile(profile),
                                active = repo.isActiveProfile(profile),
                                testing = repo.probeStateOf(profile.id) == ProbeState.TESTING,
                                locationCode = location.code,
                                locationProvisional = repo.serverLocationIsProvisional(profile),
                                familyChip = familyScan?.chip,
                                familyTone = familyScan?.let { familyChipTone(it) },
                                trailing = {
                                    ServersNodeMenu(
                                        profile = profile,
                                        repo = repo,
                                        onEdit = {
                                            renameTarget = profile
                                            renameText = stripLeadingFlag(profile.name)
                                        },
                                        onMove = { moveTarget = profile },
                                        onQr = { qrTarget = profile },
                                        onDelete = { deleteTarget = profile },
                                        onDetails = { onDetails(profile) },
                                        onCopyLink = {
                                            clipboard.setText(
                                                AnnotatedString(
                                                    profile.raw.trim().ifBlank { profile.configJson }
                                                )
                                            )
                                            repo.setRuntimeMessage("Config copied")
                                        },
                                        onCopyJson = {
                                            clipboard.setText(AnnotatedString(profile.configJson))
                                            repo.setRuntimeMessage("Xray JSON copied")
                                        }
                                    )
                                },
                                onClick = {
                                    if (repo.probeActive || repo.probeCancelling) {
                                        repo.setRuntimeMessage(
                                            "Wait until ping finishes before changing server"
                                        )
                                    } else if (repo.state == "CONNECTED" || repo.state == "CONNECTING") {
                                        onConnect(profile)
                                    } else {
                                        repo.selectProfile(profile)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (!tileLayout && !collapsed) {
                itemsIndexed(
                    items = group.profiles,
                    key = { _, profile -> "${group.key}:${profile.id}" }
                ) { index, profile ->

                    Box(
                        Modifier
                            .animateItem()
                            .marbleStaggerIn(index + 1, enabled = entranceArmed() && index < 8)
                    ) {
                        ServersNodeCard(
                            profile = profile,
                            repo = repo,
                            result = benchmarks[profile.id],
                            active = repo.isActiveProfile(profile),
                            selected = repo.isSelectedProfile(profile),
                            // MARBLE_SERVERS_HIERARCHY_V189 — the first row rounds the nested

                            firstInGroup = index == 0,
                            lastInGroup = index == group.profiles.lastIndex,
                            probeState = repo.probeStateOf(profile.id),

                            onConnect = {
                                if (repo.probeActive || repo.probeCancelling) {
                                    repo.setRuntimeMessage("Wait until ping finishes before changing server")
                                } else if (repo.state == "CONNECTED" || repo.state == "CONNECTING") {
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
            }

            if (groupIndex < groups.lastIndex) {
                item(key = "group-${group.key}-gap") { Spacer(Modifier.height(10.dp)) }
            }
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

private enum class ServersAdvancedAction {
    GROUP_BY_COUNTRY,
    ONLY_REACHABLE,
    RESET,
    REFRESH_ALL,
    RANK_ALL,

    PRUNE_FAILED_ALL,
    ALL_FILTERS
}

private enum class ServersGroupAction {
    MANAGE,
    REFRESH,
    COPY_URL,
    COPY_SERVERS,

    SHOW_ONLY,
    SHOW_ALL,

    SCAN_FAMILY,
    DELETE
}

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

/**
 * The page header: a bold headline and the two round controls that own the page's primary verbs —
 * add a server and change the order.
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — the "N groups • M servers" line that used to sit under the
 * headline is gone. A count on a line of its own is a fact the reader has to carry down the page
 * and match to a control by hand; the same two numbers now ride inside the two controls they
 * describe (the group capsule and the protocol capsule), where they answer the question the
 * control is already asking. The header is one line shorter for it.
 */
@Composable
private fun ServersTopBar(
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
            Text(
                trx("Servers"),
                color = Aether.Ink,

                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))

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

@Composable
private fun ServersRoundButton(
    icon: HomeIcon,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val tone = if (selected) Aether.Cyan else Aether.Ink
    val description = trx(label)

    val buttonElevation by animateDpAsState(
        targetValue = if (selected) 5.dp else 2.dp,
        animationSpec = MarbleMotionSpecs.Dp,
        label = "servers-round-button-elevation"
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(
                elevation = buttonElevation,
                shape = CircleShape,
                clip = false,
                ambientColor = Color(0xFF0A2540).copy(alpha = .16f),
                spotColor = if (selected) Aether.Cyan.copy(alpha = .34f) else Color(0xFF1E5FAF).copy(alpha = .22f)
            )
            .clip(CircleShape)
            .background(if (selected) Aether.Cyan.copy(alpha = .12f) else Aether.VoidElevated)
            .border(
                1.dp,
                if (selected) Aether.Cyan.copy(alpha = .45f) else Aether.GlassBorder,
                CircleShape
            )
            .semantics { contentDescription = description }
            .kineticClickable(
                role = Role.Button,
                pressScale = .92f,
                boundedShape = CircleShape,
                releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        HomeVectorIcon(icon, tone, Modifier.size(21.dp).marblePopWhen(selected))
    }
}

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
                    HomeVectorIcon(icon, tone, Modifier.size(18.dp))
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
                HomeVectorIcon(HomeIcon.CHECK, Aether.Cyan, Modifier.size(16.dp))
            }
        }
    )
}

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
            MarbleNativeSwitch(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
        }
    )
}

@Composable
private fun ServersSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val ime = rememberMarbleImeHider()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = marbleImeOptions(ImeAction.Search),
        keyboardActions = marbleImeActions(ime),
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
            HomeVectorIcon(HomeIcon.SEARCH, Aether.InkMuted, Modifier.size(20.dp))
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
                    HomeVectorIcon(HomeIcon.CANCEL, Aether.InkMuted, Modifier.size(14.dp))
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

            unfocusedBorderColor = Aether.GlassBorder,
            disabledBorderColor = Aether.GlassBorder
        )
    )
}

/**
 * The filter rail under the search field: the active group capsule, the protocol capsule, the
 * advanced-filter menu and the page-wide ping. Four controls, one compact row.
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — each capsule now carries its own count badge, so the row states
 * both *what* it is filtering by and *how much* is in that scope, and the header no longer needs a
 * line of counts of its own. Everything in the row dropped a step in size at the same time
 * (38 → 32 dp controls, 7 → 6 dp gaps, one weight of type), because a rail that repeats on every
 * scroll frame should cost the list its space, not the other way round.
 */
@Composable
private fun ServersFilterRail(
    repo: AppRepository,
    groupLabel: String,
    groupActive: Boolean,
    groupBadge: String,
    serverBadge: String,
    serverBadgeSpoken: String,
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

    val advancedCount = ServersHierarchy.activeFilterCount(
        groupByCountry = settings.serversGroupByCountry,
        onlyReachable = settings.serversOnlyReachable,
        maxPingMs = maxPing
    )

    val failedEverywhere = repo.failedNodeCountEverywhere()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            ServersFilterCapsule(
                label = groupLabel,
                tone = if (groupActive) Aether.Cyan else Aether.InkMuted,

                badge = groupBadge.takeUnless { groupActive },
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

        Box(modifier = Modifier.weight(1f, fill = false)) {
            ServersFilterCapsule(
                label = protocol.ifBlank { "All protocols" },
                tone = if (protocol.isNotBlank()) Aether.Cyan else Aether.InkMuted,
                badge = serverBadge,
                badgeSpoken = serverBadgeSpoken,
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

        Box {
            val advancedLabel = trx("Advanced filters") +
                if (advancedCount > 0) ", " + trx(ServersHierarchy.filterBadge(advancedCount)) else ""
            Box(
                modifier = Modifier
                    .size(ServersRailControlSize)
                    .clip(ServersBadgeShape)
                    .background(
                        if (advancedCount > 0 || advancedOpen) {
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
                HomeVectorIcon(HomeIcon.FILTER, Aether.Ink, Modifier.size(18.dp))
            }

            if (advancedCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .clip(CircleShape)
                        .background(Aether.Cyan)
                        .padding(horizontal = 4.dp, vertical = 0.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$advancedCount",
                        color = Aether.VoidElevated,
                        style = TextStyle(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1
                    )
                }
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

                ServersMenuItem(
                    label = "Remove failed everywhere ($failedEverywhere)",
                    icon = HomeIcon.TRASH,
                    tone = Aether.Danger,
                    enabled = !repo.busy && failedEverywhere > 0 && repo.state == "DISCONNECTED",
                    onClick = { onAdvancedAction(ServersAdvancedAction.PRUNE_FAILED_ALL) }
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

        val sweeping = repo.probeActive || repo.probeCancelling
        val pingLabel = trx(
            when {
                sweeping -> "Cancel measuring"
                groupActive -> "Ping every server in this group"
                else -> "Ping every server"
            }
        )
        Box(
            modifier = Modifier
                .size(ServersRailControlSize)
                .clip(ServersBadgeShape)
                .background(
                    when {
                        sweeping -> Aether.Danger.copy(alpha = .14f)
                        busy -> Aether.Cyan.copy(alpha = .12f)
                        else -> Aether.GlassStrong.copy(alpha = .30f)
                    }
                )
                .semantics { contentDescription = pingLabel }
                .kineticClickable(
                    enabled = sweeping || !repo.busy,
                    role = Role.Button,
                    boundedShape = ServersBadgeShape,
                    onClick = { if (sweeping) repo.cancelProbes() else onPingAll() }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                sweeping -> HomeVectorIcon(
                    HomeIcon.STOP,
                    if (repo.probeCancelling) Aether.InkFaint else Aether.Danger,
                    Modifier.size(15.dp)
                )
                busy -> MarbleExpressiveCircularIndicator(
                    modifier = Modifier.size(15.dp),
                    color = Aether.Cyan,
                    strokeWidth = 2.dp,
                    arcCount = 3
                )
                else -> HomeVectorIcon(HomeIcon.PING, Aether.Ink, Modifier.size(19.dp))
            }
        }
    }
}

/**
 * One capsule of the filter rail: the live label, its count badge, and an × that appears only when
 * the capsule is actually filtering.
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — the leading glyph is gone and the capsule dropped a step in
 * every dimension (38 → 32 dp, one weight of type, tighter padding). The space the glyph used to
 * take now holds the fact the control is about: a count badge inside the pill, on a tint of the
 * capsule's own tone. A capsule that says "All groups · 2 groups" answers the question it asks;
 * a capsule with a server glyph in front of it did not.
 */
@Composable
private fun ServersFilterCapsule(
    label: String,
    tone: Color,
    badge: String? = null,
    badgeSpoken: String? = null,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .heightIn(min = ServersRailControlSize)
            .clip(ServersPillShape)
            .background(tone.copy(alpha = .10f))
            .border(1.dp, tone.copy(alpha = .26f), ServersPillShape)
            .kineticClickable(role = Role.Button, boundedShape = ServersPillShape, onClick = onClick)
            .padding(start = 9.dp, end = if (onClear == null) 9.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            trx(label),
            color = tone,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (badge != null) {
            ServersFilterCount(text = badge, tone = tone, spoken = badgeSpoken)
        }
        if (onClear != null) {
            val clearLabel = trx("Clear")
            Box(
                modifier = Modifier
                    .size(22.dp)
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
                HomeVectorIcon(HomeIcon.CANCEL, tone, Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun ServersFilterCount(
    text: String,
    tone: Color,
    spoken: String? = null
) {
    val shape = RoundedCornerShape(7.dp)

    val spokenLabel = spoken?.let { trx(it) }
    Text(
        trx(text),
        color = tone,
        style = TextStyle(
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"
        ),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .clip(shape)
            .background(tone.copy(alpha = .16f))
            .then(
                if (spokenLabel == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = spokenLabel }
                }
            )
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    )
}

@Composable
private fun ServersProbeStrip(repo: AppRepository) {
    val done = repo.probeDone.coerceAtMost(repo.probeTotal)
    val progress = if (repo.probeTotal > 0) done.toFloat() / repo.probeTotal.toFloat() else 0f
    val refreshing = repo.refreshingSources.isNotEmpty()
    val cancelling = repo.probeCancelling
    val stopLabel = trx("Cancel measuring")
    val washEndPx = with(LocalDensity.current) { 80.dp.toPx() }

    val stripShadow = marbleNativeShadow(Aether.Cyan)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = MarbleNativeSpec.CardElevationDp.dp,
                shape = ServersCardShape,
                clip = false,
                ambientColor = stripShadow.ambient,
                spotColor = stripShadow.spot
            )
            .shadow(
                elevation = MarbleNativeSpec.ContactElevationDp.dp,
                shape = ServersCardShape,
                clip = false,
                ambientColor = stripShadow.contact,
                spotColor = stripShadow.contact
            )
            .clip(ServersCardShape)
            .background(marbleNativeCardFill())
            .background(
                Brush.verticalGradient(
                    colors = listOf(Aether.Cyan.copy(alpha = .06f), Color.Transparent),
                    startY = 0f,
                    endY = washEndPx
                )
            )
            .border(1.dp, marbleNativeOutline(), ServersCardShape)
            .padding(
                horizontal = MarbleNativeSpec.CardPaddingDp.dp,
                vertical = 12.dp
            ),
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
                Modifier.size(19.dp)
            )
            Text(
                when {
                    cancelling -> trx("Cancelling…")
                    refreshing -> trx("Refreshing sources")
                    else -> trx("Measuring servers")
                },
                color = if (cancelling) Aether.Danger else Aether.Ink,
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

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (cancelling) Aether.Danger.copy(alpha = .10f) else Aether.Danger.copy(alpha = .18f))
                    .border(1.dp, Aether.Danger.copy(alpha = .40f), CircleShape)
                    .semantics { contentDescription = stopLabel }
                    .kineticClickable(
                        enabled = !cancelling,
                        role = Role.Button,
                        boundedShape = CircleShape,
                        onClick = repo::cancelProbes
                    ),
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(
                    HomeIcon.STOP,
                    if (cancelling) Aether.Danger.copy(alpha = .45f) else Aether.Danger,
                    Modifier.size(12.dp)
                )
            }
        }

        MarbleExpressiveLinearIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = progress.coerceIn(0f, 1f),
            color = Aether.Cyan,
            trackColor = Aether.GlassStrong.copy(alpha = .40f),
            height = 4.dp
        )
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
                    Modifier.size(16.dp)
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

/**
 * One subscription card's header — level 1 of the page's hierarchy.
 *
 * Folded it is a single rounded row — chevron, name, count badge and the three controls. Open it
 * grows exactly two facts about the plan and nothing else:
 *
 *  1. the **usage line**: its text, and under it a real progress bar whose colour *is* the plan's
 *     own state — green to 70 %, amber to 90 %, red past it — instead of a number the reader has
 *     to judge for themselves;
 *  2. one quiet **secondary line**: expiry, the provider's website and the auto-update state, the
 *     three facts that are read once and then ignored, at secondary weight under the bar.
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — this card is the biggest thing on the page on purpose: the
 * largest corner radius ([ServersHierarchy.GROUP_CORNER_DP]), the boldest hairline
 * ([ServersHierarchy.GROUP_BORDER_DP]), the largest type, and three *equal* controls
 * (refresh / status / menu) on one even gap at its trailing edge. The servers stacked below are
 * deliberately smaller in every dimension; see [ServersNodeCard].
 */
@Composable
private fun ServersGroupHeader(
    group: LibraryGroup,

    modifier: Modifier = Modifier,
    subscription: Subscription?,

    attachedBelow: Boolean,
    collapsed: Boolean,
    shown: Int,
    total: Int,
    refreshing: Boolean,

    pinging: Boolean,

    cancelling: Boolean,
    autoRefresh: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onCancelPing: () -> Unit,
    onWebsite: (String) -> Unit,
    onMenu: (ServersGroupAction) -> Unit
) {
    val accent = when (group.kind) {
        else -> Aether.Cyan
    }
    val local = subscription?.url?.isBlank() == true

    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 90f else -90f,
        animationSpec = MarbleExpressiveSpecs.WaveSpringFloat,
        label = "servers-group-chevron"
    )

    // MARBLE_VISUAL_POLISH_V218 — closing a sub morphs its corners instead of snapping them.
    // The old code swapped a 16 dp top-only clip for a 20 dp all-round one in a single frame
    // while the outline kept drawing its own 16 dp geometry, so the card's corners visibly
    // fought each other for the whole collapse. One animated fraction now drives the clip, the
    // ripple bound and the frame's bottom edge together, and the closed card rounds at the same
    // GROUP_CORNER_DP its outline has always drawn — one silhouette at every frame of the morph.
    val attach by animateFloatAsState(
        targetValue = if (attachedBelow) 1f else 0f,
        animationSpec = MarbleMotionSpecs.ResponseFloat,
        label = "servers-group-attach"
    )
    val closingCorner = ServersHierarchy.attachCornerDp(attach).dp
    val shape = RoundedCornerShape(
        topStart = ServersHierarchy.GROUP_CORNER_DP.dp,
        topEnd = ServersHierarchy.GROUP_CORNER_DP.dp,
        bottomStart = closingCorner,
        bottomEnd = closingCorner
    )

    val usageTier = subscription?.let(::subscriptionUsageTier) ?: SubscriptionUsageTier.UNKNOWN
    val usageTone = usageTierTone(usageTier)
    val metered = subscription != null && ServersHierarchy.hasQuota(subscription.totalBytes)
    val usagePercent = subscription?.let {
        ServersHierarchy.usagePercent(subscriptionUsedBytes(it), it.totalBytes)
    } ?: 0

    val washEndPx = with(LocalDensity.current) { 80.dp.toPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(marbleNativeCardFill())
            .background(
                Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = .06f), Color.Transparent),
                    startY = 0f,
                    endY = washEndPx
                )
            )

            .serversStackedFrame(
                openBottomFraction = attach,
                color = serversGroupFrameColor(),
                width = ServersGroupFrameWidth,
                radius = ServersHierarchy.GROUP_CORNER_DP.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onToggle)
                .padding(
                    start = 12.dp,
                    end = 7.dp,
                    top = 12.dp,
                    // MARBLE_VISUAL_POLISH_V218 — the header's own breathing room morphs on the
                    // same fraction as its corners: 12 dp standing alone, 10 dp while it carries
                    // the server stack below.
                    bottom = 12.dp - 2.dp * attach
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(ServersHierarchy.GROUP_TILE_DP.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = .10f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .graphicsLayer { rotationZ = chevronRotation }
                        .size(15.dp)
                ) {
                    HomeVectorIcon(HomeIcon.CHEVRON, accent, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (group.flag.isNotBlank()) {
                        Text(group.flag, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    Text(
                        group.title,
                        color = Aether.Ink,
                        style = TextStyle(
                            fontSize = ServersHierarchy.GROUP_NAME_SP.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    ServersGroupCountPill(count = total, tone = accent)
                }
                if (shown != total) {
                    Text(
                        trx("$shown shown"),
                        color = Aether.InkFaint,
                        style = TextStyle(fontSize = ServersHierarchy.GROUP_SECONDARY_SP.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ServersHierarchy.GROUP_CONTROL_GAP_DP.dp)
            ) {
                if (subscription != null) {
                    val refreshLabel = trx("Refresh ${group.title}")
                    Box(
                        modifier = Modifier
                            .size(ServersHierarchy.GROUP_CONTROL_DP.dp)
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
                            MarbleExpressiveCircularIndicator(
                                modifier = Modifier.size(15.dp),
                                color = Aether.Amethyst,
                                strokeWidth = 2.dp,
                                arcCount = 3
                            )
                        } else {
                            HomeVectorIcon(
                                HomeIcon.RESET,
                                if (local) Aether.InkFaint else Aether.Amethyst,
                                Modifier.size(ServersHierarchy.GROUP_ICON_DP.dp)
                            )
                        }
                    }
                }

                val pingLabel =
                    trx(if (pinging || cancelling) "Cancel measuring" else "Ping ${group.title}")
                Box(
                    modifier = Modifier
                        .size(ServersHierarchy.GROUP_CONTROL_DP.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .semantics { contentDescription = pingLabel }
                        .kineticClickable(
                            enabled = (pinging && !cancelling) || (!pinging && group.profiles.isNotEmpty()),
                            role = Role.Button,
                            boundedShape = RoundedCornerShape(11.dp),
                            onClick = { if (pinging) onCancelPing() else onPing() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        pinging -> HomeVectorIcon(
                            HomeIcon.STOP,
                            if (cancelling) Aether.InkFaint else Aether.Danger,
                            Modifier.size(15.dp)
                        )
                        else -> HomeVectorIcon(
                            HomeIcon.PING,
                            if (group.profiles.isEmpty()) Aether.InkFaint else Aether.Emerald,
                            Modifier.size(ServersHierarchy.GROUP_ICON_DP.dp)
                        )
                    }
                }
                ServersGroupMenuButton(
                    group = group,
                    subscription = subscription,
                    autoRefresh = autoRefresh,
                    onMenu = onMenu
                )
            }
        }

        AnimatedVisibility(
            visible = !collapsed && subscription != null,
            enter = fadeIn(MarbleMotionSpecs.ResponseFloat) + expandVertically(MarbleMotionSpecs.Layout),
            exit = fadeOut(MarbleMotionSpecs.ExitFloat) + shrinkVertically(MarbleMotionSpecs.Layout)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (subscription != null && !local) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HomeVectorIcon(HomeIcon.INFO, usageTone, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                subscriptionUsageText(subscription),
                                color = Aether.Ink,
                                style = TextStyle(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum"
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (metered) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    ServersHierarchy.percentLabel(usagePercent),
                                    color = usageTone,
                                    style = TextStyle(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                        // MARBLE_SERVERS_HIERARCHY_V189 — a real bar, not a sentence. The fill

                        MarbleExpressiveLinearIndicator(
                            progress = if (metered) subscriptionUsageFraction(subscription) else 0f,
                            color = usageTone,
                            trackColor = usageTone.copy(alpha = .13f),
                            height = ServersHierarchy.USAGE_BAR_HEIGHT_DP.dp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (subscription != null && !local) {
                        Text(
                            "${trx("Expires")}: ${subscriptionExpiryText(subscription)}",
                            color = Aether.InkFaint,
                            style = TextStyle(fontSize = ServersHierarchy.GROUP_SECONDARY_SP.sp),
                            maxLines = 1
                        )
                    }
                    if (subscription != null && subscription.url.isNotBlank()) {
                        ServersWebsiteLink(
                            onClick = { onWebsite(subscription.url) }
                        )
                    }
                    Text(
                        when {
                            subscription == null -> trx("Local engine")
                            local -> trx("Local source")
                            autoRefresh -> trx("Auto-update on")
                            else -> trx("Auto-update off")
                        },
                        color = Aether.InkFaint,
                        style = TextStyle(fontSize = ServersHierarchy.GROUP_SECONDARY_SP.sp),
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
private fun ServersGroupCountPill(count: Int, tone: Color) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        trx(ServersHierarchy.serverBadge(count)),
        color = tone,
        style = TextStyle(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"
        ),
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(tone.copy(alpha = .12f))
            .border(1.dp, tone.copy(alpha = .24f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun ServersWebsiteLink(onClick: () -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    val label = trx("Website")
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Aether.GlassBorderSoft.copy(alpha = .45f))
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HomeVectorIcon(HomeIcon.GLOBE, Aether.InkMuted, Modifier.size(11.dp))
        Text(
            label,
            color = Aether.InkMuted,
            style = TextStyle(
                fontSize = ServersHierarchy.GROUP_SECONDARY_SP.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1
        )
    }
}

/**
 * The three-dot control of one group header, with its menu anchored underneath.
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — the same box and the same glyph size as the refresh and status
 * controls beside it, so the card's trailing edge is three equal siblings on one gap.
 */
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
                .size(ServersHierarchy.GROUP_CONTROL_DP.dp)
                .clip(RoundedCornerShape(11.dp))
                .semantics { contentDescription = menuLabel }
                .kineticClickable(
                    role = Role.Button,
                    boundedShape = RoundedCornerShape(11.dp),
                    onClick = { open = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            HomeVectorIcon(
                HomeIcon.MORE,
                if (open) Aether.Cyan else Aether.InkMuted,
                Modifier.size(ServersHierarchy.GROUP_ICON_DP.dp)
            )
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
            label = "Scan IPv4 / IPv6",
            icon = HomeIcon.NETWORK,
            tone = Aether.Cyan,
            detail = "${group.profiles.size} servers • resolves and dials both families",
            enabled = group.profiles.isNotEmpty(),
            onClick = {
                onDismiss()
                onMenu(ServersGroupAction.SCAN_FAMILY)
            }
        )
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

@Composable
internal fun familyChipTone(scan: IpFamilyScan): Color = when (scan.verdict) {
    IpFamilyVerdict.DUAL_OK -> Aether.Emerald
    IpFamilyVerdict.IPV6_ONLY -> Aether.Cyan
    IpFamilyVerdict.IPV4_ONLY -> Aether.Amber
    IpFamilyVerdict.IPV6_UNPROVEN -> Aether.Amber
    IpFamilyVerdict.UNREACHABLE -> Aether.Danger
    IpFamilyVerdict.UNKNOWN -> Aether.InkMuted
}

@Composable
private fun serverPingTone(latencyMs: Int, measured: Boolean, testing: Boolean): Color = when {
    testing -> Aether.Cyan
    !measured -> Aether.Danger
    latencyMs < 100 -> Aether.Emerald
    latencyMs <= 250 -> Aether.Amber
    else -> Aether.Danger
}

/**
 * One server — level 2 of the page's hierarchy: a row *inside* its subscription's card.
 *
 * MARBLE_PROTOCOL_IDENTITY — anatomy, left to right: the circular protocol tile (the type's own
 * glyph, its tone, the country flag on the rim, and the connection state on the rim colour), the
 * identity column (bold name beside a tiny state word, above the protocol badge and the endpoint),
 * the right-aligned latency stat column and the row's own menu. Swiping right still opens the
 * rename dialog for people who liked that shortcut.
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — a nested row is smaller than the card that holds it in every
 * single dimension, and the difference is the design:
 *
 * - **inset**: the row sits [ServersHierarchy.LIST_INSET_DP] inside the card's edge on both sides,
 *   on a softer container surface, so the servers read as a list *inside* a box rather than as
 *   more cards of the same size stacked under a header;
 * - **no border of its own**: rows are separated by a hairline divider, never by an outline. The
 *   only row that grows an outline is the one carrying traffic, and it grows it in the state
 *   colour, beside its own state word;
 * - **smaller type and tile**: a 30 dp protocol tile where a standalone server gets 40 dp, a 13 sp
 *   name where the subscription's name is 16 sp, and a latency readout one step under both;
 * - **quiet failure**: a server a probe could not reach fades to
 *   [ServersHierarchy.FAILED_ROW_ALPHA] and shows a cross in a muted tone. Red is reserved for
 *   things the user must act on now, and "this one did not answer" is not one of them.
 *
 * MARBLE_SELECT_IS_NOT_CONNECT_V121 — three distinct row states, none of which change geometry:
 * connected (emerald rim + "Connected"), selected (cyan rim + "Selected", the server the connect
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
    firstInGroup: Boolean,
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

    val unreachable = result != null && measured == null && !testing

    val tileStateTone: Color? = when {
        active -> Aether.Emerald
        securing -> Aether.Amethyst
        selected -> Aether.Cyan
        else -> null
    }

    val panelShape = when {
        firstInGroup && lastInGroup -> RoundedCornerShape(ServersHierarchy.LIST_CORNER_DP.dp)
        firstInGroup -> ServersListHeadShape
        lastInGroup -> ServersListTailShape
        else -> ServersListBodyShape
    }
    val rowShape = if (lastInGroup) ServersGroupTailShape else ServersGroupBodyShape

    val rowFill by animateColorAsState(
        targetValue = when {
            active -> Aether.Emerald.copy(alpha = .10f)
            securing -> Aether.Amethyst.copy(alpha = .10f)
            selected -> Aether.Cyan.copy(alpha = .08f)
            else -> Aether.Cyan.copy(alpha = 0f)
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "servers-row-state"
    )
    val rowAlpha = if (unreachable) ServersHierarchy.FAILED_ROW_ALPHA else 1f
    val country = ServersQuery.countryOf(profile)
    val name = displayServerName(profile.name, profile.host, profile.scheme)
    val flag = leadingFlagGlyph(profile.name) ?: country.flag

    val locationCode = repo.serverLocation(profile).code
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(marbleNativeCardFill())
            .serversStackedFrame(
                openTop = true,
                openBottom = !lastInGroup,
                color = serversGroupFrameColor(),
                width = ServersGroupFrameWidth,
                radius = ServersHierarchy.GROUP_CORNER_DP.dp
            )
    ) {
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = !repo.busy,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val tone = Aether.Amethyst
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ServersHierarchy.LIST_INSET_DP.dp)
                    .clip(panelShape)
                    .background(tone.copy(alpha = .12f))
                    .padding(horizontal = 14.dp),
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .kineticClickable(
                        enabled = !repo.busy && !active,
                        role = Role.Button,
                        boundedShape = rowShape,
                        onClick = onConnect
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = ServersHierarchy.LIST_INSET_DP.dp)
                        .clip(panelShape)
                        .heightIn(min = MarbleNativeSpec.RowDenseHeightDp.dp)
                        .background(marbleNativeContainerHigh())
                        .background(rowFill)
                        .then(
                            if (tileStateTone != null) {
                                Modifier.border(1.dp, tileStateTone.copy(alpha = .45f), panelShape)
                            } else {
                                Modifier
                            }
                        )
                        .padding(
                            start = ServersHierarchy.ROW_PAD_START_DP.dp,
                            end = 3.dp,
                            top = ServersHierarchy.ROW_PAD_VERTICAL_DP.dp,
                            bottom = ServersHierarchy.ROW_PAD_VERTICAL_DP.dp
                        )
                        .alpha(rowAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProtocolTile(
                        scheme = profile.scheme,
                        size = ServersHierarchy.ROW_TILE_DP.dp,
                        flag = flag.takeIf { it.isNotBlank() && it != ServerCountry.UNKNOWN.flag },
                        flagCode = locationCode,

                        locationTrust = marbleLocationTrustOf(
                            hasSessionReport = false,
                            hasMeasuredCode = locationCode.isNotBlank(),
                            measuredIsProvisional = repo.serverLocationIsProvisional(profile),
                            hasLabelGlyph = false
                        ),
                        stateTone = tileStateTone
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                name,
                                color = Aether.Ink,
                                style = TextStyle(
                                    fontSize = ServersHierarchy.ROW_NAME_SP.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (active) {
                                ServerStateChip(trx("Connected"), Aether.Emerald)
                            } else if (securing) {
                                ServerStateChip(trx("Securing"), Aether.Amethyst)
                            } else if (selected) {
                                ServerStateChip(trx("Selected"), Aether.Cyan)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ProtocolBadge(
                                scheme = profile.scheme,
                                label = ServersQuery.badge(profile)
                            )
                            Text(
                                ServersQuery.address(profile),
                                color = Aether.InkFaint,
                                style = TextStyle(fontSize = ServersHierarchy.ROW_DETAIL_SP.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            repo.ipFamilyScan(profile)?.let { scan ->
                                ServerStateChip(scan.chip, familyChipTone(scan))
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))
                    ServerPingStat(
                        latencyMs = latency,
                        measured = measured != null,
                        testing = testing,

                        attempted = unreachable,
                        compact = true,
                        quietFailure = true
                    )

                    Spacer(Modifier.width(1.dp))
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

            if (!lastInGroup) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ServersHierarchy.LIST_INSET_DP.dp)
                        .background(marbleNativeContainerHigh())
                ) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = serversRowDividerColor(),
                        modifier = Modifier.padding(
                            start = (ServersHierarchy.ROW_PAD_START_DP + ServersHierarchy.ROW_TILE_DP + 9f).dp,
                            end = 12.dp
                        )
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    }
}

/**
 * The latency readout. It never changes size.
 *
 * MARBLE_NO_PHANTOM_PING_V121 — a server that has never been measured reads "—" in a quiet tone,
 * not "0 ms" in red. A freshly imported subscription has no measurements at all, and printing a
 * zero next to every one of its servers claimed both a measurement and an impossible latency.
 * Red is reserved for a probe that actually ran and actually failed.
 *
 * MARBLE_PING_AIR_V152 — the pill background is gone: on a stacked subscription row the tinted
 * slab read as a second chip fighting the protocol badge and the state word, and at 28 dp tall
 * the glyph/number/unit triad was pressed against its own walls. What is left is the measurement
 * itself in its quality tone — nothing to compete with, nothing to crop. The fixed minimum width
 * survives so every row's number still right-aligns to the same column.
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
            // MARBLE_PING_AIR_V152 — width first, decoration never: the number owns the space and

            .widthIn(min = 62.dp)
            .height(28.dp)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center
    ) {
        when {
            testing -> MarbleExpressiveCircularIndicator(
                modifier = Modifier.size(12.dp),
                color = tone,
                strokeWidth = 1.6.dp,
                arcCount = 2
            )

            !measured -> Text(
                if (attempted) "✕" else "—",
                color = tone,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )

            else -> Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HomeVectorIcon(HomeIcon.PING, tone, Modifier.size(15.dp))
                Text(
                    "$latencyMs",
                    color = tone,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1
                )
                Text(
                    trx("ms"),
                    color = tone.copy(alpha = .74f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun ServersNodeMenu(
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

    val familyScan = repo.ipFamilyScan(profile)

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
                    MarbleImeReleaseOnDispose(rememberMarbleImeHider())
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
            // MARBLE_SERVERS_HIERARCHY_V189 — a nested row's own control, one step under the

            size = 28.dp,
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

                label = "Scan IPv4 / IPv6",
                icon = HomeIcon.NETWORK,
                tone = familyScan?.let { familyChipTone(it) } ?: Aether.Cyan,
                detail = familyScan?.let { "${it.chip} • ${it.headline}" }
                    ?: "Resolve and dial both families on this server",
                enabled = !repo.busy,
                onClick = {
                    open = false
                    repo.scanIpFamily(profile)
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

@Composable
private fun IpFamilyScanDialog(scan: IpFamilyScan, onDismiss: () -> Unit) {
    val tone = familyChipTone(scan)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        shape = ServersCardShape,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(trx("Address family scan"), color = Aether.Ink)
                Text(
                    scan.headline,
                    color = tone,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.XS)) {
                IpFamilyLine(
                    label = "IPv6",
                    value = when {
                        scan.ipv6Ok -> buildString {
                            append("${scan.ipv6Address} • ${scan.ipv6LatencyMs} ms")
                            if (scan.ipv6JitterMs >= 0) append(" ±${scan.ipv6JitterMs}ms")
                            if (scan.ipv6SuccessRate in 0.01f..0.99f) append(" ${(scan.ipv6SuccessRate*100).toInt()}%")
                            if (scan.nat64Detected) append(" [NAT64]")
                        }
                        scan.hasIpv6 -> "${scan.ipv6Address} • no answer on port${if (scan.failureReason.isNotBlank()) " (${scan.failureReason})" else ""}"
                        else -> trx("No AAAA record") + if (scan.dnsResultKind.isNotBlank()) " (${scan.dnsResultKind})" else ""
                    },
                    tone = if (scan.ipv6Ok) Aether.Emerald else Aether.InkMuted
                )
                IpFamilyLine(
                    label = "IPv4",
                    value = when {
                        scan.ipv4Ok -> buildString {
                            append("${scan.ipv4Address} • ${scan.ipv4LatencyMs} ms")
                            if (scan.ipv4JitterMs >= 0) append(" ±${scan.ipv4JitterMs}ms")
                            if (scan.ipv4SuccessRate in 0.01f..0.99f) append(" ${(scan.ipv4SuccessRate*100).toInt()}%")
                        }
                        scan.hasIpv4 -> "${scan.ipv4Address} • no answer on port"
                        else -> trx("No A record")
                    },
                    tone = if (scan.ipv4Ok) Aether.Emerald else Aether.InkMuted
                )
                IpFamilyLine(
                    label = trx("This network"),
                    value = if (scan.underlayHasIpv6) {
                        trx("Carries IPv6") + if (scan.underlayRttMs >= 0) " • ${scan.underlayRttMs}ms" else ""
                    } else {
                        trx("IPv4 only • no IPv6 route")
                    },
                    tone = if (scan.underlayHasIpv6) Aether.Cyan else Aether.Amber
                )
                IpFamilyLine(
                    label = "Confidence",
                    value = "${scan.confidence}% • ${scan.confidenceTier.name.lowercase()}${if (scan.dnsWitnesses > 0) " • ${scan.dnsWitnesses} witnesses" else ""}",
                    tone = when {
                        scan.confidence >= 80 -> Aether.Emerald
                        scan.confidence >= 50 -> Aether.Cyan
                        else -> Aether.Amber
                    }
                )
                if (scan.bogonFiltered > 0) {
                    IpFamilyLine(
                        label = "Filtered",
                        value = "${scan.bogonFiltered} bogon IPs dropped",
                        tone = Aether.Danger
                    )
                }
                IpFamilyLine(
                    label = "Mode",
                    value = scan.scanMode.lowercase() + if (scan.scanMode == "DEEP") " • full jitter & NAT64 check" else "",
                    tone = Aether.InkMuted
                )
                if (scan.fasterFamily.isNotBlank()) {
                    IpFamilyLine(
                        label = trx("Faster"),
                        value = if (scan.fasterFamily == "ipv6") "IPv6" else "IPv4",
                        tone = Aether.Emerald
                    )
                }
                Text(
                    scan.advice,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall
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
private fun IpFamilyLine(label: String, value: String, tone: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            trx(label),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            value,
            color = tone,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun IpFamilyGroupDialog(summary: IpFamilySummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        shape = ServersCardShape,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(trx("Address family scan"), color = Aether.Ink)
                Text(
                    "${summary.ipv6Capable}/${summary.scanned} ${trx("IPv6-capable")}",
                    color = if (summary.ipv6Capable > 0) Aether.Emerald else Aether.Amber,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.XS)) {
                IpFamilyLine(
                    "Dual stack",
                    summary.dual.toString(),
                    if (summary.dual > 0) Aether.Emerald else Aether.InkMuted
                )
                IpFamilyLine(
                    "IPv6 only",
                    summary.ipv6Only.toString(),
                    if (summary.ipv6Only > 0) Aether.Cyan else Aether.InkMuted
                )
                IpFamilyLine(
                    "IPv4 only",
                    summary.ipv4Only.toString(),
                    if (summary.ipv4Only > 0) Aether.Amber else Aether.InkMuted
                )
                if (summary.ipv6Unproven > 0) {
                    IpFamilyLine("IPv6 unproven", summary.ipv6Unproven.toString(), Aether.Amber)
                }
                if (summary.unreachable > 0) {
                    IpFamilyLine("No answer", summary.unreachable.toString(), Aether.Danger)
                }
                if (summary.unknown > 0) {
                    IpFamilyLine("Unresolved", summary.unknown.toString(), Aether.InkMuted)
                }
                if (summary.avgConfidence > 0) {
                    IpFamilyLine(
                        "Avg confidence",
                        "${summary.avgConfidence}% • ${summary.highConfidence} high-conf",
                        if (summary.avgConfidence >= 70) Aether.Emerald else Aether.Amber
                    )
                }
                if (summary.nat64Count > 0) {
                    IpFamilyLine("NAT64", "${summary.nat64Count} synthetic", Aether.Cyan)
                }
                Text(
                    if (summary.ipv6Capable == 0) {
                        trx(
                            "No server in this group answered over IPv6, so Force IPv6 dials " +
                                "them over IPv4 and keeps IPv6 for destinations."
                        )
                    } else {
                        trx(
                            "Servers marked v6 or v4+v6 can carry a strict IPv6 connection on " +
                                "an IPv6-capable network."
                        )
                    },
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall
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
private fun ServersMoveDialog(
    profile: ProxyProfile,
    repo: AppRepository,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
    val failedPingCount = repo.failedSubscriptionNodeCount(subscription.id, "SMART")
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
                val manageIme = rememberMarbleImeHider()
                MarbleImeReleaseOnDispose(manageIme)
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = onNameChange,
                    label = { Text(trx("Source name")) },
                    singleLine = true,
                    shape = ServersCardShape,
                    keyboardOptions = marbleImeOptions(ImeAction.Next),
                    keyboardActions = marbleImeActions(manageIme),
                    modifier = Modifier.fillMaxWidth(),
                    colors = marbleOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = onUrlChange,
                    label = { Text(trx("Subscription URL")) },
                    singleLine = true,
                    shape = ServersCardShape,
                    keyboardOptions = marbleImeOptions(ImeAction.Done),
                    keyboardActions = marbleImeActions(manageIme),
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
                    label = "Remove failed Smart ($failedPingCount)",
                    color = Aether.Danger,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !repo.busy && failedPingCount > 0 && disconnected
                ) { onPrune("SMART") }
                CyberButton(
                    label = "Remove failed tunnel ($failedTunnelCount)",
                    color = Aether.Danger,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !repo.busy && failedTunnelCount > 0 && disconnected
                ) { onPrune("TUNNEL") }
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

@Composable
private fun ManualBucketCleanupDialog(
    repo: AppRepository,
    onPrune: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val total = repo.subscriptionNodeCount("manual")
    val failedPingCount = repo.failedSubscriptionNodeCount("manual", "SMART")
    val failedTunnelCount = repo.failedSubscriptionNodeCount("manual", "TUNNEL")
    val disconnected = repo.state == "DISCONNECTED"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        shape = ServersCardShape,
        title = {
            Column {
                Text(trx("Manual servers"), color = Aether.Ink)
                Text(
                    "$total " + trx("servers") + " • " + trx("added by link, QR or file"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    trx("Servers you added yourself live here and are never replaced by a subscription refresh."),
                    color = Aether.InkMuted,
                    style = settingsBodyStyle()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CyberButton(
                        label = "Copy servers",
                        color = Aether.Cyan,
                        modifier = Modifier.weight(1f),
                        enabled = total > 0
                    ) {
                        clipboard.setText(AnnotatedString(repo.subscriptionRawText("manual")))
                        repo.setRuntimeMessage("$total server links copied")
                    }
                    CyberButton(
                        label = "View servers",
                        color = Aether.Emerald,
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.selectLibrarySource("manual")
                        onDismiss()
                    }
                }
                HorizontalDivider(color = Aether.GlassBorderSoft)
                Text(
                    trx("Clean failed tests"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
                CyberButton(
                    label = "Remove failed Smart ($failedPingCount)",
                    color = Aether.Danger,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !repo.busy && failedPingCount > 0 && disconnected
                ) { onPrune("SMART") }
                CyberButton(
                    label = "Remove failed tunnel ($failedTunnelCount)",
                    color = Aether.Danger,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !repo.busy && failedTunnelCount > 0 && disconnected
                ) { onPrune("TUNNEL") }
                if (!disconnected) {
                    Text(
                        trx("Disconnect before removing failed servers"),
                        color = Aether.Amber,
                        style = settingsBodyStyle()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            MarbleDialogAction(label = "Close", tone = Aether.InkMuted, onClick = onDismiss)
        }
    )
}

private data class ServersOption(
    val value: String,
    val label: String = value,
    val enabled: Boolean = true,
    val detail: String? = null
)

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

            modifier = Modifier
                .fillMaxWidth()
                .marbleImeInert(),
            trailingIcon = {
                Box(
                    Modifier
                        .graphicsLayer { rotationZ = if (open) 180f else 0f }
                        .size(15.dp)
                ) {
                    HomeVectorIcon(HomeIcon.CHEVRON, Aether.InkFaint, Modifier.fillMaxSize())
                }
            }
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
                            HomeVectorIcon(HomeIcon.ACTIVE, Aether.Cyan, Modifier.size(18.dp))
                        }
                    }
                )
            }
        }
    }
}

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
        { Text(trx(placeholder)) }
    }
    val ime = rememberMarbleImeHider()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(trx(label)) },
        placeholder = hint,
        singleLine = singleLine,
        isError = isError,

        keyboardOptions = marbleImeOptions(if (singleLine) ImeAction.Done else ImeAction.None),
        keyboardActions = marbleImeActions(ime),
        shape = ServersCardShape,
        colors = marbleOutlinedTextFieldColors(),
        trailingIcon = trailing
    )
}

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
                ServersField(
                    label = "Verify peer certificate by name",
                    value = draft.verifyPeerCertByName,
                    onValueChange = { names -> update { it.copy(verifyPeerCertByName = names) } },
                    placeholder = "example.com, alt.example.com",
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "Certificate fingerprint (SHA-256)",
                    value = draft.pinnedPeerCertSha256,
                    onValueChange = { pins -> update { it.copy(pinnedPeerCertSha256 = pins) } },
                    placeholder = "hex or base64, comma separated",
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    trx("Pinning replaces CA verification: names map to verifyPeerCertByName, fingerprints to pinnedPeerCertSha256."),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
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
                    ServersField(
                        label = "Verify peer certificate by name",
                        value = draft.verifyPeerCertByName,
                        onValueChange = { names -> update { it.copy(verifyPeerCertByName = names) } },
                        placeholder = "example.com, alt.example.com",
                        modifier = Modifier.fillMaxWidth()
                    )
                    ServersField(
                        label = "Certificate fingerprint (SHA-256)",
                        value = draft.pinnedPeerCertSha256,
                        onValueChange = { pins -> update { it.copy(pinnedPeerCertSha256 = pins) } },
                        placeholder = "hex or base64, comma separated",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        trx("Pinning replaces CA verification: names map to verifyPeerCertByName, fingerprints to pinnedPeerCertSha256."),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall
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
                ServersField(
                    label = "Verify peer certificate by name",
                    value = draft.verifyPeerCertByName,
                    onValueChange = { names -> update { it.copy(verifyPeerCertByName = names) } },
                    placeholder = "example.com, alt.example.com",
                    modifier = Modifier.fillMaxWidth()
                )
                ServersField(
                    label = "Certificate fingerprint (SHA-256)",
                    value = draft.pinnedPeerCertSha256,
                    onValueChange = { pins -> update { it.copy(pinnedPeerCertSha256 = pins) } },
                    placeholder = "hex or base64, comma separated",
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    trx("Pinning replaces CA verification: names map to verifyPeerCertByName, fingerprints to pinnedPeerCertSha256."),
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
        MarbleNativeSwitch(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
        Text(
            description,
            color = if (checked) Aether.Amber else Aether.InkMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2
        )
    }
}

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
                    Modifier.size(16.dp)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ServersAddPage(
    repo: AppRepository,
    initialMode: String,
    onImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var subName by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }

    val addSheetIme = rememberMarbleImeHider()
    MarbleImeReleaseOnDispose(addSheetIme)
    var importText by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(libraryIntakeTarget(repo)) }
    val clipboard = LocalClipboardManager.current

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
                        HomeVectorIcon(HomeIcon.CANCEL, Aether.Ink, Modifier.size(18.dp))
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
                        "chain" -> ManualChainEditor(repo, target, onDismiss)

                        "subscription" -> Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.M)) {
                            OutlinedTextField(
                                value = subName,
                                onValueChange = { subName = it },
                                label = { Text(trx("Source name")) },
                                singleLine = true,
                                shape = ServersCardShape,
                                colors = marbleOutlinedTextFieldColors(),
                                keyboardOptions = marbleImeOptions(ImeAction.Next),
                                keyboardActions = marbleImeActions(addSheetIme),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = subUrl,
                                onValueChange = { subUrl = it },
                                label = { Text(trx("Subscription URL")) },
                                singleLine = true,
                                shape = ServersCardShape,
                                colors = marbleOutlinedTextFieldColors(),
                                keyboardOptions = marbleImeOptions(ImeAction.Done),
                                keyboardActions = marbleImeActions(addSheetIme),
                                modifier = Modifier.fillMaxWidth()
                            )
                            CyberButton(
                                label = "Add subscription",
                                color = Aether.Emerald,
                                variant = PrismButtonVariant.Primary,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = subscriptionReady && !repo.busy
                            ) {
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
                            val landing = repo.intakeTargetOrManual(target)
                            val addedId = repo.importClipboard(importText, landing)
                            importText = ""
                            repo.selectLibrarySource(addedId ?: landing)
                            onDismiss()
                        }
                    }

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

        Text("${trx("ORDERED HOPS")} • ${hops.size}", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
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
                    Text("${index + 1}. ${profile?.name ?: trx("Unavailable node")}", color = Aether.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trx(if (index == hops.lastIndex) "EXIT" else "HOP"), color = if (index == hops.lastIndex) Aether.Emerald else Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
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
    val ime = rememberMarbleImeHider()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(trx(label)) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 18,
        modifier = modifier,

        keyboardOptions = marbleImeOptions(if (singleLine) ImeAction.Done else ImeAction.None),
        keyboardActions = marbleImeActions(ime),
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

        modifier = Modifier.fillMaxSize(),
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
                    HomeVectorIcon(HomeIcon.DETAILS, Aether.Cyan, Modifier.size(19.dp))
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

        {
            val sessions = repo.usageSessions.filter { it.profileId == current.id }
            val storedBytes = sessions.sumOf { it.bytes }
            val liveBytes = if (active && repo.state == "CONNECTED") repo.sessionBytes else 0L
            val last = sessions.firstOrNull()
            if (storedBytes > 0L || liveBytes > 0L) {
                item {
                    val lastLine = last?.let {
                        val seconds = (it.endedAtMs - it.startedAtMs) / 1000L
                        val duration = if (seconds >= 60) trx("${seconds / 60} min") else trx("${seconds} sec")
                        "${formatBytes(it.bytes)} · $duration"
                    }
                    HoloGlass(Modifier.fillMaxWidth()) {
                        SectionLabel(trx("Data usage"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniMetric(trx("Total"), formatBytes(storedBytes + liveBytes), "", Modifier.weight(1f))
                            MiniMetric(trx("Sessions"), sessions.size.toString(), "", Modifier.weight(1f))
                        }
                        DetailRow(
                            trx("Last session"),
                            lastLine ?: formatBytes(liveBytes)
                        )
                        if (liveBytes > 0L) {
                            DetailRow(trx("Current session"), formatBytes(liveBytes))
                        }
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
                        Modifier.size(19.dp)
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
                    Modifier.size(19.dp)
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

private fun dockSlotSubtitle(target: DockSlotTarget): String = when (target.kind) {
    DockSlotKind.PULSE -> "The live route, and the tools that act on the library"
    DockSlotKind.SOURCE -> when {
        target.isEmpty -> "This source has no servers"
        else -> "${target.profiles.size} servers • ${target.displayName}"
    }
    DockSlotKind.CONFIG -> when {
        target.isEmpty -> "No config chosen yet"
        else -> "${target.profiles.first().subscriptionName} • ${ServersQuery.address(target.profiles.first())}"
    }
}

@Composable
private fun DockFlagTile(profile: ProxyProfile?, size: Dp = 38.dp, flagCode: String? = null) {
    val flag = profile?.name?.let(::leadingFlagGlyph)
        ?: profile?.host?.let(::countryGlyph)?.takeIf { it.isNotBlank() && it != "◈" }
    val shape = RoundedCornerShape(size / 3)
    val code = flagCode?.takeIf { it.isNotBlank() }?.takeIf { CountryFlagSupported(it) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(marbleNativeCardFill())
            .border(1.dp, marbleNativeOutline(), shape),
        contentAlignment = Alignment.Center
    ) {
        if (code != null) {
            CountryFlagCircle(code = code, size = size - 2.dp)
        } else {
            Text(
                text = flag
                    ?: profile?.scheme?.trim()?.take(1)?.uppercase()?.ifBlank { "M" }
                    ?: "🌐",
                fontSize = (size.value * .42f).sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DockSlotCard(
    tone: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = homeCloudDark()
    val washEndPx = with(LocalDensity.current) { 150.dp.toPx() }
    val slotShadow = marbleNativeShadow(tone)
    val slotOutline = marbleNativeOutline()
    val slotRim = lerp(slotOutline, tone, .22f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = MarbleNativeSpec.CardElevationDp.dp,
                shape = ServersCardShape,
                clip = false,
                ambientColor = slotShadow.ambient,
                spotColor = slotShadow.spot
            )
            .shadow(
                elevation = MarbleNativeSpec.ContactElevationDp.dp,
                shape = ServersCardShape,
                clip = false,
                ambientColor = slotShadow.contact,
                spotColor = slotShadow.contact
            )
            .clip(ServersCardShape)
            .background(marbleNativeCardFill())
            .background(
                Brush.verticalGradient(
                    colors = listOf(tone.copy(alpha = if (dark) .06f else .035f), Color.Transparent),
                    startY = 0f,
                    endY = washEndPx
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(slotRim, slotOutline)),
                ServersCardShape
            )
            .padding(MarbleNativeSpec.CardPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content
    )
}

@Composable
private fun CustomDockPage(
    repo: AppRepository,
    deck: DeckEvidence,
    actions: HomeActions,
    target: DockSlotTarget,
    chrome: DockSlotChrome,
    onConnect: (ProxyProfile) -> Unit,
    onDetails: (ProxyProfile) -> Unit,
    onDialog: (String) -> Unit,
    onCustomize: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()

    val entranceArmed = rememberMarbleEntranceWindow()
    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = dockClearance() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "dock-slot-header") {
            Box(Modifier.marbleStaggerIn(0, enabled = entranceArmed())) {
                MarbleCompactTopBar(
                    title = chrome.caption,
                    subtitle = dockSlotSubtitle(target),
                    actionLabel = "Customize",
                    actionIcon = HomeIcon.MODE,
                    onAction = onCustomize
                )
            }
        }

        when (target.kind) {
            DockSlotKind.PULSE -> {
                item(key = "dock-slot-pulse-live") {
                    Box(Modifier.marbleStaggerIn(1, enabled = entranceArmed())) {
                        DockPulseLiveCard(repo, deck, actions)
                    }
                }

                item(key = "dock-slot-pulse-quality") {
                    Box(Modifier.marbleStaggerIn(2, enabled = entranceArmed())) {
                        DockPulseQualityCard(repo, deck)
                    }
                }
                item(key = "dock-slot-pulse-metrics") {
                    Box(Modifier.marbleStaggerIn(3, enabled = entranceArmed())) {
                        DockPulseMetricsCard(repo, deck)
                    }
                }
                item(key = "dock-slot-pulse-tools") {
                    Box(Modifier.marbleStaggerIn(4, enabled = entranceArmed())) {
                        DockPulseToolsCard(repo, actions, onDialog)
                    }
                }
            }

            DockSlotKind.SOURCE -> {
                item(key = "dock-slot-source-head") {
                    Box(Modifier.marbleStaggerIn(1, enabled = entranceArmed())) {
                        DockSourceHeaderCard(repo, target, onConnect)
                    }
                }
                if (target.isEmpty) {
                    item(key = "dock-slot-source-empty") {
                        Box(Modifier.marbleStaggerIn(2, enabled = entranceArmed())) {
                            DockSlotEmptyCard(
                                icon = HomeIcon.LIBRARY,
                                title = "This source has no servers",
                                detail = "Everything it publishes was removed, or it was never filled. " +
                                    "Pick another source, or add servers from the Servers page.",
                                actionLabel = "Pick another source",
                                onAction = onCustomize
                            )
                        }
                    }
                } else {
                    val ordered = ServersQuery.sort(
                        profiles = target.profiles,
                        mode = repo.settings.nodeSortMode,
                        reverse = repo.settings.nodeSortReverse,
                        benchmarks = repo.benchmarks.associateBy { it.profileId }
                    )

                    val measurements = repo.benchmarks.associateBy { it.profileId }
                    itemsIndexed(
                        ordered,
                        key = { _, profile -> "dock-slot-src-${profile.id}-${profile.subscriptionId}" }
                    ) { index, profile ->
                        Box(
                            Modifier
                                .animateItem()
                                .marbleStaggerIn(index + 1, enabled = entranceArmed() && index < 8)
                        ) {
                            DockNodeRow(
                                profile = profile,
                                result = measurements[profile.id],
                                probeState = repo.probeStateOf(profile.id),
                                active = repo.isActiveProfile(profile),
                                selected = repo.isSelectedProfile(profile),
                                flagCode = repo.serverLocation(profile).code,
                                onSelect = {
                                    if (repo.probeActive || repo.probeCancelling) {
                                        repo.setRuntimeMessage("Wait until ping finishes before changing server")
                                    } else if (repo.state == "CONNECTED" || repo.state == "CONNECTING") {
                                        onConnect(profile)
                                    } else {
                                        repo.selectProfile(profile)
                                    }
                                },
                                onDetails = { onDetails(profile) }
                            )
                        }
                    }
                }
            }

            DockSlotKind.CONFIG -> {
                item(key = "dock-slot-config-head") {
                    Box(Modifier.marbleStaggerIn(1, enabled = entranceArmed())) {
                        DockConfigCard(
                            repo = repo,
                            target = target,
                            onConnect = onConnect,
                            onDetails = onDetails,
                            onCustomize = onCustomize
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockSlotEmptyCard(
    icon: HomeIcon,
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    DockSlotCard(tone = Aether.Cyan) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            HomeVectorIcon(icon, Aether.Cyan, Modifier.size(20.dp))
            Text(
                trx(title),
                color = Aether.Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Text(trx(detail), color = Aether.InkMuted, style = MaterialTheme.typography.labelSmall)
        CyberButton(
            label = actionLabel,
            color = Aether.Cyan,
            icon = HomeIcon.MODE,
            variant = PrismButtonVariant.Primary,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAction
        )
    }
}

@Composable
private fun DockPulseLiveCard(
    repo: AppRepository,
    deck: DeckEvidence,
    actions: HomeActions
) {
    val evidence = deck.evidence
    val tone = homeStateTone(evidence)
    val uptime = rememberUptimeLabel(evidence.connectedSinceMs)

    DockSlotCard(tone = tone) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(tone)
            )
            Text(
                homeStatusText(evidence),
                color = tone,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (repo.liveRouteScore >= 0) {
                Text(
                    "${repo.liveRouteScore}%",
                    color = tone,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            DockFlagTile(
                evidence.profile,
                size = 36.dp,
                flagCode = evidence.profile?.let { repo.serverLocation(it).code }
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    evidence.nodeName.ifBlank { Tr.now.chooseRoute },
                    color = Aether.Ink,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        evidence.profile?.let { ServersQuery.badge(it) },
                        evidence.sourceName.takeIf { it.isNotBlank() },
                        uptime.takeIf { evidence.connected }
                    ).joinToString(" • "),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .clip(ServersPillShape)
                    .border(1.dp, tone.copy(alpha = .34f), ServersPillShape)
                    .kineticClickable(
                        role = Role.Button,
                        boundedShape = ServersPillShape,
                        onClick = { actions.onPingGroup() }
                    )
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                HomeVectorIcon(HomeIcon.PING, tone, Modifier.size(14.dp))
                Text(
                    trx("Ping group"),
                    color = tone,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        CyberButton(
            label = when {
                evidence.disconnecting -> "Disconnecting…"
                evidence.connecting -> "Cancel connect"
                evidence.connected -> "Disconnect"
                else -> "Connect"
            },
            color = when {
                evidence.disconnecting || evidence.connecting -> Aether.Amber
                evidence.connected -> Aether.Danger
                else -> Aether.Emerald
            },
            icon = if (evidence.connected || evidence.connecting) HomeIcon.STOP else HomeIcon.POWER,
            variant = PrismButtonVariant.Primary,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !evidence.disconnecting,
            onClick = actions.onToggleConnection
        )
    }
}

@Composable
private fun DockPulseQualityCard(repo: AppRepository, deck: DeckEvidence) {
    val evidence = deck.evidence
    val score = repo.liveRouteScore
    val tone = when {
        evidence.blocked -> Aether.Danger
        score >= 75 -> Aether.Emerald
        score >= 45 -> Aether.Amber
        score >= 0 -> Aether.Danger
        else -> Aether.Cyan
    }

    val fill by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = MarbleMotionSpecs.ProgressFloat,
        label = "dock-quality-meter"
    )

    DockSlotCard(tone = tone) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            HomeVectorIcon(HomeIcon.QUALITY, tone, Modifier.size(17.dp))
            Text(
                trx("Route quality"),
                color = Aether.Ink,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            if (score >= 0) {
                Text(
                    "$score%",
                    color = tone,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            } else {
                Text(
                    trx("Not measured yet"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(ServersPillShape)
                .background(Aether.GlassBorderSoft.copy(alpha = .6f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(ServersPillShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(tone.copy(alpha = .55f), tone)
                        )
                    )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            HoloBadge(
                if (evidence.stabilityClass.isNotBlank()) evidence.stabilityClass else "STABLE",
                if (evidence.stabilityClass.isNotBlank() &&
                    evidence.stabilityClass.uppercase() != "STABLE"
                ) Aether.Amber else Aether.Emerald,
                compact = true
            )
            HoloBadge(
                if (repo.liveJitterMs > 0) "JITTER ${repo.liveJitterMs}ms" else "JITTER —",
                Aether.Amethyst,
                compact = true
            )
            if (evidence.injectedResetSuspected) {
                HoloBadge("RESET SUSPECTED", Aether.Danger, compact = true)
            }
        }
        Text(
            trx(
                "The score is the live route's own verdict — throughput, delay and steadiness "
                    + "measured while you use it, refreshed by the next ping."
            ),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DockPulseMetricsCard(repo: AppRepository, deck: DeckEvidence) {
    val evidence = deck.evidence
    val measured = evidence.pingState == ConnectionPingState.MEASURED
    val uptime = rememberUptimeLabel(evidence.connectedSinceMs)

    DockSlotCard(tone = Aether.Cyan) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniMetric(
                label = "Down",
                value = compactRate(evidence.downBps),
                unit = "/s",
                modifier = Modifier.weight(1f),
                accent = Aether.Emerald,
                icon = HomeIcon.DOWNLOAD
            )
            MiniMetric(
                label = "Up",
                value = compactRate(evidence.upBps),
                unit = "/s",
                modifier = Modifier.weight(1f),
                accent = Aether.Cyan,
                icon = HomeIcon.UPLOAD
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniMetric(
                label = "Ping",
                value = if (measured && evidence.pingMs > 0) "${evidence.pingMs}" else "—",
                unit = if (measured && evidence.pingMs > 0) "ms" else "",
                modifier = Modifier.weight(1f),
                accent = homePingTone(evidence, Aether.CyanBright),
                icon = HomeIcon.PING
            )
            MiniMetric(
                label = "Jitter",
                value = if (repo.liveJitterMs > 0) "${repo.liveJitterMs}" else "—",
                unit = if (repo.liveJitterMs > 0) "ms" else "",
                modifier = Modifier.weight(1f),
                accent = Aether.Amethyst,
                icon = HomeIcon.JITTER
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniMetric(
                label = "Uptime",
                value = if (evidence.connected) uptime else "—",
                unit = "",
                modifier = Modifier.weight(1f),
                accent = Aether.SlateBright,
                icon = HomeIcon.ROUTE
            )
            MiniMetric(
                label = "Session",
                value = if (evidence.sessionBytes > 0) formatBytes(evidence.sessionBytes) else "—",
                unit = "",
                modifier = Modifier.weight(1f),
                accent = Aether.Amber,
                icon = HomeIcon.DOWNLOAD
            )
        }
    }
}

@Composable
private fun DockPulseToolsCard(
    repo: AppRepository,
    actions: HomeActions,
    onDialog: (String) -> Unit
) {
    val report = repo.bugReport

    DockSlotCard(tone = Aether.Amethyst) {
        Text(
            trx("Tools"),
            color = Aether.Ink,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        if (repo.inlineProgressActive) {
            ServersProbeStrip(repo)
        }

        Text(
            trx("LIBRARY"),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CyberButton(
                label = "Rank everything",
                color = Aether.Amethyst,
                icon = HomeIcon.RANK,
                variant = PrismButtonVariant.Primary,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = { repo.smartRank() }
            )
            CyberButton(
                label = "Ping everything",
                color = Aether.Cyan,
                icon = HomeIcon.PING,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = { repo.testAll() }
            )
        }
        CyberButton(
            label = "Refresh sources",
            color = Aether.Emerald,
            icon = HomeIcon.DOWNLOAD,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = { repo.refreshAll() }
        )

        HorizontalDivider(color = Aether.GlassBorderSoft)

        Text(
            trx("DIAGNOSTICS"),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CyberButton(
                label = if (report == null) "Run Bug Finder" else "Bug report",
                color = Aether.Amber,
                icon = HomeIcon.SHIELD,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (report == null) repo.runBugFinder() else onDialog("Bug Finder")
                }
            )
            CyberButton(
                label = "Privacy audit",
                color = Aether.CyanBright,
                icon = HomeIcon.PRIVACY,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    repo.audit()
                    onDialog("Privacy")
                }
            )
        }
        CyberButton(
            label = "IP details",
            color = Aether.SlateBright,
            icon = HomeIcon.INFO,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = { actions.onIpDetails() }
        )
        if (report != null) {
            Text(
                report.headline,
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DockSourceHeaderCard(
    repo: AppRepository,
    target: DockSlotTarget,
    onConnect: (ProxyProfile) -> Unit
) {
    val subscription = repo.subscriptions.firstOrNull { it.id == target.id }

    val refreshable = target.id != "manual"

    val measurements = remember(target.id, repo.benchmarks) {
        repo.benchmarks.associateBy { it.profileId }
    }
    val measuredCount = target.profiles.count { measurements[it.id]?.let { r -> r.success > 0 } == true }
    val fastest = target.profiles
        .mapNotNull { profile -> measurements[profile.id]?.let { profile to it } }
        .filter { (_, r) -> r.success > 0 && r.latencyMs >= 20 }
        .minByOrNull { (_, r) -> r.latencyMs }

    DockSlotCard(tone = Aether.Emerald) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            HomeVectorIcon(HomeIcon.NODES, Aether.Emerald, Modifier.size(19.dp))
            Text(
                target.displayName,
                color = Aether.Ink,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${target.profiles.size}",
                color = Aether.Emerald,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (subscription != null) {
            Text(
                "${trx("Used")} ${subscriptionUsageText(subscription)} • ${trx("Expires")} ${
                    subscriptionExpiryText(subscription)
                }",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }

        Text(
            buildString {
                append(target.profiles.size)
                append(" ")
                append(trx("servers"))
                append(" • ")
                if (measuredCount == 0) {
                    append(trx("Not measured yet"))
                } else {
                    append(measuredCount)
                    append(" ")
                    append(trx("measured"))
                    fastest?.let { (profile, result) ->
                        append(" • ")
                        append(trx("best"))
                        append(" ${result.latencyMs.toInt()} ms (${stripLeadingFlag(profile.name)})")
                    }
                }
            },
            color = Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CyberButton(
                label = "Ping this source",
                color = Aether.Cyan,
                icon = HomeIcon.PING,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = { repo.testSource(target.id) }
            )
            CyberButton(
                label = "Rank this source",
                color = Aether.Amethyst,
                icon = HomeIcon.RANK,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = { repo.smartRankSource(target.id) }
            )
        }
        if (fastest != null) {
            val (bestProfile, bestResult) = fastest
            CyberButton(
                label = "Connect fastest",
                detail = "${bestResult.latencyMs.toInt()} ms",
                color = Aether.Emerald,
                icon = HomeIcon.POWER,
                variant = PrismButtonVariant.Primary,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !repo.busy && !repo.probeActive,
                onClick = {
                    if (repo.probeActive || repo.probeCancelling) {
                        repo.setRuntimeMessage("Wait until ping finishes before changing server")
                    } else if (repo.state == "CONNECTED" || repo.state == "CONNECTING") {
                        onConnect(bestProfile)
                    } else {
                        repo.selectProfile(bestProfile)
                    }
                }
            )
        }
        if (refreshable) {
            CyberButton(
                label = "Refresh this source",
                color = Aether.Emerald,
                icon = HomeIcon.DOWNLOAD,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { repo.refreshLibrarySource(target.id) }
            )
        }
    }
}

@Composable
private fun DockNodeRow(
    profile: ProxyProfile,
    result: BenchmarkResult?,
    probeState: ProbeState,
    active: Boolean,
    selected: Boolean,
    flagCode: String = "",
    onSelect: () -> Unit,
    onDetails: () -> Unit
) {
    val measured = result?.takeIf { it.success > 0 && it.latencyMs >= 20 }

    val shape = marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)
    val outline = marbleNativeOutline()
    val tone = when {
        active -> Aether.Emerald
        selected -> Aether.Cyan
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MarbleNativeSpec.TouchTargetDp.dp)
            .clip(shape)
            .background(
                if (tone == Color.Transparent) marbleNativeCardFill() else marbleNativeSelectedFill(tone)
            )
            .border(
                1.dp,
                if (tone == Color.Transparent) outline else tone.copy(alpha = .34f),
                shape
            )
            .clickable(onClick = onSelect)
            .padding(
                horizontal = MarbleNativeSpec.RowPaddingHorizontalDp.dp - 4.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        DockFlagTile(profile, size = 32.dp, flagCode = flagCode)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stripLeadingFlag(profile.name),
                color = Aether.Ink,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected || active) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(ServersQuery.badge(profile), ServersQuery.address(profile))
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ServersPingCapsule(
            latencyMs = measured?.latencyMs?.toInt() ?: 0,
            measured = measured != null,
            testing = probeState == ProbeState.TESTING,
            attempted = result != null && measured == null
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .kineticClickable(
                    role = Role.Button,
                    boundedShape = CircleShape,
                    onClick = onDetails
                ),
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(HomeGlyph.INFO, Aether.InkMuted, Modifier.size(13.dp))
        }
    }
}

@Composable
private fun DockConfigCard(
    repo: AppRepository,
    target: DockSlotTarget,
    onConnect: (ProxyProfile) -> Unit,
    onDetails: (ProxyProfile) -> Unit,
    onCustomize: () -> Unit
) {
    val pinned = target.profiles.firstOrNull()
    if (pinned == null) {
        DockSlotEmptyCard(
            icon = HomeIcon.SERVER,
            title = "No config chosen yet",
            detail = "Pick one saved config for this slot, and it will be one tap away from the bar " +
                "for as long as you keep it there.",
            actionLabel = "Choose a config",
            onAction = onCustomize
        )
        return
    }

    val benchmarks = repo.benchmarks.associateBy { it.profileId }
    val result = benchmarks[pinned.id]
    val measured = result?.takeIf { it.success > 0 && it.latencyMs >= 20 }
    val selected = repo.isSelectedProfile(pinned)
    val active = repo.isActiveProfile(pinned)
    val tone = when {
        active -> Aether.Emerald
        selected -> Aether.Cyan
        else -> Aether.SlateBright
    }

    DockSlotCard(tone = tone) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DockFlagTile(pinned, size = 40.dp, flagCode = repo.serverLocation(pinned).code)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    stripLeadingFlag(pinned.name),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(ServersQuery.badge(pinned), ServersQuery.address(pinned))
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pinned.subscriptionName,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ServersPingCapsule(
                latencyMs = measured?.latencyMs?.toInt() ?: 0,
                measured = measured != null,
                testing = repo.probeStateOf(pinned.id) == ProbeState.TESTING,
                attempted = result != null && measured == null
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CyberButton(
                label = when {
                    active -> "Disconnect"
                    selected -> "Connect this config"
                    else -> "Use this config"
                },
                color = if (active) Aether.Danger else Aether.Emerald,
                icon = if (active) HomeIcon.POWER else HomeIcon.CHECK,
                variant = PrismButtonVariant.Primary,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    when {
                        active -> repo.stopVpn()
                        selected -> onConnect(pinned)
                        else -> repo.selectProfile(pinned)
                    }
                }
            )
            CyberButton(
                label = "Ping",
                color = Aether.Cyan,
                icon = HomeIcon.PING,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    repo.pingProfiles(
                        profiles = listOf(pinned),
                        scopeLabel = stripLeadingFlag(pinned.name),
                        scopeId = pinned.subscriptionId
                    )
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CyberButton(
                label = "Details",
                color = Aether.Amethyst,
                icon = HomeIcon.DETAILS,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = { onDetails(pinned) }
            )
            CyberButton(
                label = "Change config",
                color = Aether.SlateBright,
                icon = HomeIcon.MODE,
                compact = true,
                modifier = Modifier.weight(1f),
                onClick = onCustomize
            )
        }
        if (selected && !active) {
            Text(
                trx("Selected: the connect button on Home and the dock's own Connect act on this config."),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

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

private object SettingsPages {
    const val HUB = "hub"
    const val THEME = "theme"
    const val HOME_STYLE = "home-style"
    const val TYPEFACE = "typeface"
    const val LANGUAGE = "language"
    const val INFORMATION = "information"

    const val ROUTING = "routing"

    const val TESTS = "tests"

    const val TESTS_CARD = "Testing"

    const val DOCK_SLOT = "dock-slot"
    private const val WORKSPACE = "workspace"

    fun workspace(tab: SettingsWorkspaceTab, focus: String? = null): String =
        listOf(WORKSPACE, tab.name, focus.orEmpty()).joinToString(":")

    fun isWorkspace(page: String): Boolean = page.startsWith("$WORKSPACE:")

    fun workspaceTab(page: String): SettingsWorkspaceTab =
        rememberedSettingsTab(page.split(":").getOrElse(1) { "" })

    fun workspaceFocus(page: String): String? =
        page.split(":").getOrElse(2) { "" }.ifBlank { null }

    private const val CARD = "card"

    fun card(tab: SettingsWorkspaceTab, title: String): String =
        listOf(CARD, tab.name, title).joinToString(":")

    fun isCard(page: String): Boolean = page.startsWith("$CARD:")

    fun cardTab(page: String): SettingsWorkspaceTab =
        rememberedSettingsTab(page.split(":", limit = 3).getOrElse(1) { "" })

    fun cardTitle(page: String): String =
        page.split(":", limit = 3).getOrElse(2) { "" }
}

@Composable
private fun settingsTitleStyle(): TextStyle =
    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)

@Composable
private fun settingsRowTitleStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

@Composable
private fun settingsBodyStyle(): TextStyle =
    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal)

private fun openExternal(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun SettingsSubPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    content: @Composable ColumnScope.() -> Unit
) {
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
                    HomeVectorIcon(HomeIcon.BACK, Aether.Cyan, Modifier.size(18.dp))
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
                        // MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one option, at most one sentence.
                        MarbleCopy.oneSentence(trx(subtitle)),
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

private val LocalSettingsSectionTone = compositionLocalOf { HomeCloud.BrandAccent }

@Composable
private fun settingsSectionTone(): Color = LocalSettingsSectionTone.current

@Composable
private fun SettingsHubCard(
    title: String,
    tone: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = marbleNativeShape(MarbleNativeSpec.CardCornerDp.dp)
    val dark = homeCloudDark()
    val nativeShadow = marbleNativeShadow(tone)
    val outline = marbleNativeOutline()
    val litRim = lerp(outline, tone, .26f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = MarbleNativeSpec.CardElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = nativeShadow.ambient,
                spotColor = nativeShadow.spot
            )
            .shadow(
                elevation = MarbleNativeSpec.ContactElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = nativeShadow.contact,
                spotColor = nativeShadow.contact
            )
            .clip(shape)
            .background(marbleNativeCardFill())
            .background(
                Brush.verticalGradient(
                    colors = listOf(tone.copy(alpha = if (dark) .06f else .035f), Color.Transparent)
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(litRim, outline)),
                shape
            )
            .padding(
                start = MarbleNativeSpec.CardPaddingDp.dp,
                end = MarbleNativeSpec.CardPaddingDp.dp,
                top = 13.dp,
                bottom = 13.dp
            ),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                trx(title),
                color = Aether.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    // MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one option, at most one sentence.
                    MarbleCopy.oneSentence(trx(subtitle)),
                    color = marbleNativeInkVariant(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        CompositionLocalProvider(LocalSettingsSectionTone provides tone) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsHubRow(
    title: String,
    subtitle: String,
    tone: Color,
    onClick: () -> Unit,
    badge: String = "",
    preview: @Composable () -> Unit
) {
    val shape = marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MarbleNativeSpec.RowTwoLineHeightDp.dp)
            .clip(shape)
            .background(if (pressed) Aether.Ink.copy(alpha = .07f) else Color.Transparent)
            .kineticClickable(
                role = Role.Button,
                boundedShape = shape,
                showIndication = false,
                interactionSource = pressSource,
                onClick = onClick
            )
            .padding(
                horizontal = MarbleNativeSpec.RowPaddingHorizontalDp.dp,
                vertical = MarbleNativeSpec.RowPaddingVerticalDp.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    trx(title),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (badge.isNotBlank()) {
                    Text(
                        trx(badge),
                        color = marbleNativeInkVariant(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(ServersBadgeShape)
                            .background(marbleNativeContainerHigh())
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                // MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one option, at most one sentence.
                MarbleCopy.oneSentence(trx(subtitle)),
                color = marbleNativeInkVariant(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(30.dp),
            contentAlignment = Alignment.Center
        ) {
            preview()
        }

        MarbleNativeChevron(marbleNativeOutline().copy(alpha = .9f))
    }
}

@Composable
private fun SettingsHubSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    val tone = settingsSectionTone()
    MarbleNativeListItem(
        title = title,
        subtitle = MarbleCopy.oneSentence(trx(subtitle)),
        tone = tone,
        selected = checked,
        role = Role.Switch,
        onClick = { onChecked(!checked) },
        trailing = {
            MarbleNativeSwitch(
                checked = checked,
                onCheckedChange = { next -> onChecked(next) }
            )
        }
    )
}

// ---------------------------------------------------------------------------------------------

// ---------------------------------------------------------------------------------------------

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

        Triple(AppTheme.SYSTEM, "System", listOf(Color(0xFF0A0A14), Color(0xFFF4F8FD), Aether.Cyan)),
        Triple(AppTheme.LIGHT, "Light", listOf(Color(0xFFF4F8FD), Color.White, Aether.Cyan)),
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
                val fill = if (selected) ramp[2].copy(alpha = if (homeCloudDark()) .16f else .08f).compositeOver(marbleNativeContainerHigh()) else marbleNativeContainerHigh()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(fill)
                        .border(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) ramp[2] else marbleNativeOutline().copy(alpha = .65f),
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

                    HomeStyle.ROUTE_ATELIER -> Aether.Cyan
                }
                val shape = RoundedCornerShape(11.dp)
                val fill = if (selected) tone.copy(alpha = if (homeCloudDark()) .16f else .08f).compositeOver(marbleNativeContainerHigh()) else marbleNativeContainerHigh()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(fill)
                        .border(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) tone else marbleNativeOutline().copy(alpha = .65f),
                            shape
                        )
                        .kineticClickable(role = Role.Button, boundedShape = shape) {
                            repo.updateSettings(repo.settings.copy(homeStyle = style.id))
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
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

@Composable
private fun SettingsStyleMotif(style: HomeStyle, tone: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (style) {
            HomeStyle.IOS_SLIDER -> {
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.15f), Size(w * 0.70f, h * 0.25f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.25f), Offset(w * 0.15f, h * 0.45f), Size(w * 0.70f, h * 0.20f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone, Offset(w * 0.15f, h * 0.75f), Size(w * 0.70f, h * 0.20f), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
            }

            HomeStyle.IOS_FLOATING -> {
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.15f), Size(w * 0.70f, h * 0.25f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.25f), Offset(w * 0.15f, h * 0.45f), Size(w * 0.55f, h * 0.45f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawCircle(tone, h * 0.15f, Offset(w * 0.82f, h * 0.75f))
            }

            HomeStyle.IOS_EMBOSSED -> {
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.12f), Size(w * 0.70f, h * 0.20f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawCircle(tone, h * 0.22f, Offset(w * 0.50f, h * 0.52f))
                drawRoundRect(tone.copy(alpha = 0.25f), Offset(w * 0.15f, h * 0.78f), Size(w * 0.70f, h * 0.18f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            }

            HomeStyle.IOS_MODULAR -> {
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.15f), Size(w * 0.32f, h * 0.32f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.53f, h * 0.15f), Size(w * 0.32f, h * 0.32f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.4f), Offset(w * 0.15f, h * 0.55f), Size(w * 0.32f, h * 0.32f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(tone, Offset(w * 0.53f, h * 0.55f), Size(w * 0.32f, h * 0.32f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            }

            HomeStyle.ROUTE_ATELIER -> {
                drawRoundRect(tone.copy(alpha = 0.30f), Offset(w * 0.15f, h * 0.10f), Size(w * 0.70f, h * 0.30f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                val midY = h * 0.25f
                drawCircle(tone, h * 0.055f, Offset(w * 0.28f, midY))
                drawCircle(tone.copy(alpha = 0.55f), h * 0.055f, Offset(w * 0.50f, midY))
                drawCircle(tone.copy(alpha = 0.25f), h * 0.055f, Offset(w * 0.72f, midY))
                drawLine(tone.copy(alpha = 0.5f), Offset(w * 0.33f, midY), Offset(w * 0.45f, midY), strokeWidth = 1.2.dp.toPx())
                drawRoundRect(tone, Offset(w * 0.15f, h * 0.52f), Size(w * 0.70f, h * 0.18f), CornerRadius(3.dp.toPx(), 3.dp.toPx()))
                drawRoundRect(tone.copy(alpha = 0.22f), Offset(w * 0.15f, h * 0.78f), Size(w * 0.70f, h * 0.16f), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun SettingsDockSlotPreview(
    icon: DockSlotIcon,
    enabled: Boolean,
    tone: Color,
    showStatusBadge: Boolean,
    badgeTone: Color
) {
    val mark = Aether.Void
    Canvas(Modifier.size(width = 34.dp, height = 20.dp)) {
        val w = size.width
        val h = size.height
        val slot = (w - 3 * 3.dp.toPx()) / 4f
        val shape = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        repeat(3) { index ->
            drawRoundRect(
                color = tone.copy(alpha = .28f),
                topLeft = Offset(index * (slot + 3.dp.toPx()), h * .30f),
                size = Size(slot, h * .40f),
                cornerRadius = shape
            )
        }
        drawRoundRect(
            color = tone.copy(alpha = if (enabled) .85f else .16f),
            topLeft = Offset(3 * (slot + 3.dp.toPx()), h * .20f),
            size = Size(slot, h * .60f),
            cornerRadius = shape
        )
        if (enabled) {
            val cx = 3 * (slot + 3.dp.toPx()) + slot / 2f
            val cy = h * .50f
            val stroke = 1.4.dp.toPx()
            when (icon) {
                DockSlotIcon.PULSE -> drawLine(
                    mark,
                    Offset(cx - slot * .22f, cy),
                    Offset(cx + slot * .22f, cy),
                    stroke,
                    StrokeCap.Round
                )

                DockSlotIcon.SPARK -> drawCircle(
                    mark,
                    radius = stroke * .9f,
                    center = Offset(cx, cy)
                )

                DockSlotIcon.LAYERS -> {
                    val gap = stroke * 1.6f
                    drawLine(mark, Offset(cx - slot * .22f, cy - gap), Offset(cx + slot * .22f, cy - gap), stroke, StrokeCap.Round)
                    drawLine(mark, Offset(cx - slot * .22f, cy + gap), Offset(cx + slot * .22f, cy + gap), stroke, StrokeCap.Round)
                }

                DockSlotIcon.BEARING -> drawLine(
                    mark,
                    Offset(cx - slot * .18f, cy + slot * .18f),
                    Offset(cx + slot * .18f, cy - slot * .18f),
                    stroke,
                    StrokeCap.Round
                )
            }
        }
        if (enabled && showStatusBadge) {
            val badgeCenter = Offset(3 * (slot + 3.dp.toPx()) + slot * .78f, h * .24f)
            drawCircle(mark, radius = 2.0.dp.toPx(), center = badgeCenter)
            drawCircle(badgeTone, radius = 1.45.dp.toPx(), center = badgeCenter)
        }
    }
}

@Composable
private fun SettingsTypefacePreview(fontId: String, tone: Color) {
    Text(
        "Aa",
        color = tone,
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = previewFontFamily(fontId)),
        maxLines = 1
    )
}

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

            style = MaterialTheme.typography.labelSmall.copy(fontFamily = VazirFamily),
            maxLines = 1
        )
    }
}

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

@Composable
private fun SettingsTestPreview(tone: Color) {
    PrismSignalMeter(bars = 3, tone = tone, modifier = Modifier.size(width = 20.dp, height = 16.dp))
}

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

// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsHub(
    repo: AppRepository,
    onNavigate: (String) -> Unit,
    collapsed: Set<String>,
    onToggleGroup: (String) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val entranceArmed = rememberMarbleEntranceWindow()
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) repo.writeBackup(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) repo.restoreBackup(uri) }

    val groups = settingsHubGroups(
        repo = repo,
        onNavigate = onNavigate,
        onBackup = { backupLauncher.launch("marbleng-backup.json") },
        onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) }
    )
    val keys = groups.map { it.key }
    val openCount = SettingsHubPolicy.openCount(collapsed, keys)
    val allOpen = SettingsHubPolicy.allOpen(collapsed, keys)

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
            // MARBLE_SETTINGS_HUB_TRIM_V163 — the title stands alone; the version lives on the

            MarbleCompactTopBar(
                title = "Settings",
                subtitle = if (allOpen) {
                    trx("All sections open")
                } else {
                    "$openCount / ${groups.size} " + trx("sections open")
                },
                actionLabel = if (allOpen) trx("Collapse all") else trx("Expand all"),
                actionIcon = HomeIcon.CHEVRON,
                onAction = { if (allOpen) onCollapseAll() else onExpandAll() }
            )
        }

        groups.forEachIndexed { index, group ->
            item(key = "hub-group-${group.key}") {
                SettingsHubGroupCard(
                    group = group,
                    expanded = SettingsHubPolicy.isOpen(collapsed, group.key),
                    onToggle = { onToggleGroup(group.key) },
                    modifier = Modifier.marbleStaggerIn(index + 1, enabled = entranceArmed())
                )
            }
        }
    }
}

@Composable
private fun settingsHubGroups(
    repo: AppRepository,
    onNavigate: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
): List<SettingsHubGroupSpec> {
    val t = Tr.now
    val settings = repo.settings
    val activeTheme = parseAppTheme(settings.theme)
    val activeStyle = parseHomeStyle(settings.homeStyle)
    val activeFont = parseAppFont(settings.fontFamily)
    val activeLanguage = parseAppLanguage(settings.appLanguage)

    fun section(
        title: String,
        subtitle: String = "",
        content: @Composable () -> Unit
    ): SettingsHubSectionSpec = SettingsHubSectionSpec(title, subtitle, content)

    return listOf(

        SettingsHubGroupSpec(
            key = "home",
            title = t.categoryHome,
            subtitle = "The presentation of the connection page and what it shows",
            tone = Aether.Cyan,
            icon = { tone -> HomeVectorIcon(HomeIcon.MODE, tone, Modifier.size(19.dp)) },
            sections = listOf(
                section(
                    title = t.homeStyleTitle,
                    subtitle = "Which of the five presentations Home uses"
                ) {
                    SettingsStyleMiniRow(repo)
                    SettingsHubRow(
                        title = t.homeStyleTitle,
                        subtitle = homeStyleLabel(activeStyle),
                        tone = Aether.Cyan,
                        onClick = { onNavigate(SettingsPages.HOME_STYLE) }
                    ) { SettingsStyleMotif(activeStyle, Aether.Cyan, Modifier.size(width = 34.dp, height = 20.dp)) }
                },
                section(
                    title = trx("Home widgets"),
                    subtitle = "Live readouts on the connection page"
                ) {
                    SettingSwitch(
                        title = "Live speed on Home",
                        subtitle = "Download and upload while connected",
                        checked = settings.homeSpeedWidgetEnabled
                    ) { repo.updateSettings(repo.settings.copy(homeSpeedWidgetEnabled = it)) }

                    SettingSwitch(
                        title = "Show data usage",
                        subtitle = "Current and previous session",
                        checked = settings.homeShowDataUsage
                    ) { repo.updateSettings(repo.settings.copy(homeShowDataUsage = it)) }

                    SettingSwitch(
                        title = "Ambient page motion",
                        subtitle = "Pause decorative motion",
                        checked = settings.homeAmbientBackdrop
                    ) { repo.updateSettings(repo.settings.copy(homeAmbientBackdrop = it)) }
                },
                section(
                    title = trx("Home layout"),
                    subtitle = "The Customize row of Home style 4"
                ) {
                    SettingSwitch(
                        title = "Show the Customize button",
                        subtitle = "Show the Customize row at the top of Home style 4.",
                        checked = !settings.modularHideCustomizerButton
                    ) { repo.updateSettings(repo.settings.copy(modularHideCustomizerButton = !it)) }
                },
                section(
                    title = trx("Ping controls"),
                    subtitle = "What each ping button on the connection page measures"
                ) {
                    HomePingControlsSettings(repo)
                }
            )
        ),

        SettingsHubGroupSpec(
            key = "connection",
            title = t.categoryConnection,
            subtitle = "The tunnel, what it carries and when it starts",
            tone = Aether.Emerald,
            icon = { tone -> HomeVectorIcon(HomeIcon.TUNNEL, tone, Modifier.size(19.dp)) },
            sections = listOf(
                section(
                    title = trx("Engine"),
                    subtitle = "Which core builds the tunnel"
                ) {
                    SettingsHubRow(
                        title = "Engine & tunnel",
                        subtitle = "Core switch, Xray and sing-box options",
                        tone = Aether.Amber,
                        badge = CoreEngineInfo.displayName(repo.activeCoreEngine),
                        onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.ENGINE)) }
                    ) { HomeVectorIcon(HomeIcon.TUNNEL, Aether.Amber, Modifier.size(22.dp)) }
                },
                section(
                    title = trx("Tunnel & proxy"),
                    subtitle = "How the tunnel listens and what it dials"
                ) {
                    SettingsHubRow(
                        title = "Connection",
                        subtitle = "Tunnel, proxy, port",
                        tone = Aether.Cyan,
                        onClick = {
                            onNavigate(SettingsPages.card(SettingsWorkspaceTab.GENERAL, "Connection"))
                        }
                    ) { HomeVectorIcon(HomeIcon.TUNNEL, Aether.Cyan, Modifier.size(22.dp)) }
                },
                section(
                    title = trx("Routing"),
                    subtitle = "Which traffic goes through the tunnel"
                ) {
                    SettingsHubRow(
                        title = "Network & routing",
                        subtitle = "${trx("DNS, split tunnel and geo rules")} • ${
                            // The same four words the Routing page itself uses, so the hub preview
                            // and the page can never disagree about which mode is active.
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
                },
                section(
                    title = trx("Automatic connect"),
                    subtitle = "When MarbleNG connects without being asked"
                ) {
                    SettingSwitch(
                        title = "Connect to best after scan",
                        subtitle = "Connect after a completed scan",
                        checked = settings.autoConnectBestAfterScan
                    ) { repo.updateSettings(repo.settings.copy(autoConnectBestAfterScan = it)) }
                }
            )
        ),

        SettingsHubGroupSpec(
            key = "measurement",
            title = trx("Measurement & servers"),
            subtitle = "How servers are judged and drawn",
            tone = Aether.Amber,
            icon = { tone -> HomeVectorIcon(HomeIcon.BENCHMARK, tone, Modifier.size(19.dp)) },
            sections = listOf(
                section(
                    title = trx("Ping & ranking"),
                    subtitle = "The measurement every server is judged by"
                ) {
                    SettingsHubRow(
                        title = "Tests & ranking",
                        subtitle = "Ping, speed and smart ranking",
                        tone = Aether.Cyan,
                        onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.TESTS)) }
                    ) { SettingsTestPreview(Aether.Cyan) }
                },
                section(
                    title = trx("Server library"),
                    subtitle = "Where servers come from and how they are drawn"
                ) {
                    SettingSwitch(
                        title = "Auto-detect server locations",
                        subtitle = "",
                        checked = settings.serverLocationAutoDetect
                    ) { repo.updateSettings(repo.settings.copy(serverLocationAutoDetect = it)) }

                    SettingsHubRow(
                        title = "Server cards",
                        subtitle = serverLayoutSubtitle(repo.settings),
                        tone = Aether.Amethyst,
                        onClick = {
                            onNavigate(SettingsPages.card(SettingsWorkspaceTab.GENERAL, "Server cards"))
                        }
                    ) { HomeVectorIcon(HomeIcon.SERVER, Aether.Amethyst, Modifier.size(22.dp)) }
                }
            )
        ),

        SettingsHubGroupSpec(
            key = "data",
            title = t.categoryData,
            subtitle = "Everything MarbleNG keeps, and where it came from",
            tone = Aether.CyanBright,
            icon = { tone -> HomeVectorIcon(HomeIcon.LIBRARY, tone, Modifier.size(19.dp)) },
            sections = listOf(
                section(
                    title = trx("Backup"),
                    subtitle = "Write the whole configuration to a file, or read one back"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CyberButton(
                            label = "Back up",
                            color = Aether.Cyan,
                            icon = HomeIcon.DETAILS,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        ) { onBackup() }
                        CyberButton(
                            label = "Restore",
                            color = Aether.Amethyst,
                            icon = HomeIcon.RESET,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        ) { onRestore() }
                    }
                },
                section(
                    title = trx("Sources"),
                    subtitle = "Where servers come from and how often they refresh"
                ) {
                    SettingsHubRow(
                        title = "Subscriptions",
                        subtitle = "Refresh & sources",
                        tone = Aether.Emerald,
                        onClick = {
                            onNavigate(SettingsPages.card(SettingsWorkspaceTab.GENERAL, "Subscriptions"))
                        }
                    ) { HomeVectorIcon(HomeIcon.LIBRARY, Aether.Emerald, Modifier.size(22.dp)) }
                }
            )
        ),

        SettingsHubGroupSpec(
            key = "appearance",
            title = t.categoryAppearance,
            subtitle = "Theme, navigation and type",
            tone = Aether.Amethyst,
            icon = { tone -> HomeVectorIcon(HomeIcon.PALETTE, tone, Modifier.size(19.dp)) },
            sections = listOf(
                section(
                    title = trx("Theme"),
                    subtitle = "Colours, and where they come from"
                ) {
                    SettingsThemeMiniRow(repo)
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
                },
                section(
                    title = trx("Navigation"),
                    subtitle = "The bar that is on screen on every page"
                ) {
                    SettingsHubRow(
                        title = "Navigation bar",
                        subtitle = dockSettingsSubtitle(settings),
                        tone = Aether.Emerald,
                        onClick = {
                            onNavigate(SettingsPages.card(SettingsWorkspaceTab.GENERAL, "Navigation bar"))
                        }
                    ) { HomeVectorIcon(HomeIcon.SERVER, Aether.Emerald, Modifier.size(22.dp)) }

                    SettingsHubRow(
                        title = "Fourth tab",
                        subtitle = dockSlotSettingsSubtitle(
                            settings,
                            dockSlotTarget(repo, parseDockSlotKind(settings.dockSlotKind))
                        ),
                        tone = dockSlotAccentTone(parseDockSlotAccent(settings.dockSlotAccent)),
                        onClick = { onNavigate(SettingsPages.DOCK_SLOT) }
                    ) {
                        SettingsDockSlotPreview(
                            icon = parseDockSlotIcon(settings.dockSlotIcon),
                            enabled = settings.dockSlotEnabled,
                            tone = dockSlotAccentTone(parseDockSlotAccent(settings.dockSlotAccent)),
                            showStatusBadge = settings.dockSlotShowStatusBadge,
                            badgeTone = dockSlotStateTone(repo.state)
                        )
                    }
                },
                section(
                    title = trx("Text & language"),
                    subtitle = "Size, typeface and language of every screen"
                ) {
                    TextScaleChoice(repo)
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
            )
        ),

        SettingsHubGroupSpec(
            key = "system",
            title = t.categorySystem,
            subtitle = "Alerts, background access and the app itself",
            tone = Aether.SlateBright,
            icon = { tone -> HomeVectorIcon(HomeIcon.STATUS, tone, Modifier.size(19.dp)) },
            sections = listOf(
                section(
                    title = trx("Alerts"),
                    subtitle = "What MarbleNG is allowed to tell you"
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
                    ) { HomeVectorIcon(HomeIcon.STATUS, Aether.Cyan, Modifier.size(22.dp)) }

                    SettingsHubRow(
                        title = "Background access",
                        subtitle = "Fixes interrupted VPN in background",
                        tone = Aether.Amber,
                        onClick = { onNavigate(SettingsPages.workspace(SettingsWorkspaceTab.SYSTEM)) }
                    ) { HomeVectorIcon(HomeIcon.POWER, Aether.Amber, Modifier.size(22.dp)) }
                },
                section(
                    title = trx("About"),
                    subtitle = "Versions, diagnostics and the report an issue asks for"
                ) {
                    SettingsHubRow(
                        title = t.informationTitle,
                        subtitle = t.informationDetail,
                        tone = Aether.Amethyst,
                        onClick = { onNavigate(SettingsPages.INFORMATION) }
                    ) { SettingsVersionPreview(Aether.Amethyst) }
                }
            )
        )
    )
}

@Composable
private fun SettingsHubGroupCard(
    group: SettingsHubGroupSpec,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = marbleNativeShape(MarbleNativeSpec.CardCornerDp.dp)
    val dark = homeCloudDark()
    val washEndPx = with(LocalDensity.current) { 150.dp.toPx() }
    val groupShadow = marbleNativeShadow(group.tone)
    val groupOutline = marbleNativeOutline()
    val groupRim = lerp(groupOutline, group.tone, if (dark) .30f else .22f)

    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = MarbleMotionSpecs.DockFloat,
        label = "settings-group-chevron"
    )
    val headerShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = MarbleNativeSpec.CardElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = groupShadow.ambient,
                spotColor = groupShadow.spot
            )
            .shadow(
                elevation = MarbleNativeSpec.ContactElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = groupShadow.contact,
                spotColor = groupShadow.contact
            )
            .clip(shape)
            .background(marbleNativeCardFill())
            .background(
                Brush.verticalGradient(
                    colors = listOf(group.tone.copy(alpha = if (dark) .06f else .035f), Color.Transparent),
                    startY = 0f,
                    endY = washEndPx
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(groupRim, groupOutline)),
                shape
            )
            .padding(
                start = MarbleNativeSpec.CardPaddingDp.dp,
                end = MarbleNativeSpec.CardPaddingDp.dp,
                top = 13.dp,
                bottom = 13.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .kineticClickable(role = Role.Button, boundedShape = headerShape, onClick = onToggle)
                .semantics {
                    contentDescription = group.title
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(MarbleNativeSpec.LeadingContainerDp.dp)
                    .clip(CircleShape)
                    .background(
                        group.tone.copy(alpha = if (dark) .20f else .12f)
                            .compositeOver(marbleNativeCardFill())
                    ),
                contentAlignment = Alignment.Center
            ) {
                group.icon(group.tone)
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    trx(group.title),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one option, at most one sentence.
                    MarbleCopy.oneSentence(trx(group.subtitle)),
                    color = marbleNativeInkVariant(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                "${SettingsHubPolicy.sectionCount(group)}",
                color = marbleNativeInkVariant(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(ServersBadgeShape)
                    .background(marbleNativeContainerHigh())
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )

            Box(Modifier.rotate(chevronTurn)) {
                MarbleNativeChevron(group.tone.copy(alpha = .80f))
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expressiveExpandVertically(),
            exit = expressiveCollapseVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                group.sections.forEach { section ->
                    SettingsHubSubSectionCard(tone = group.tone, section = section)
                }
            }
        }
    }
}

@Composable
private fun SettingsHubSubSectionCard(
    tone: Color,
    section: SettingsHubSectionSpec
) {
    val shape = marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)
    val insetFill = marbleNativeContainerHigh()
    val subOutline = marbleNativeOutline()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(insetFill.copy(alpha = 0.65f))
            .border(1.dp, subOutline.copy(alpha = 0.75f), shape)
            .padding(
                start = MarbleNativeSpec.RowPaddingHorizontalDp.dp - 4.dp,
                end = MarbleNativeSpec.RowPaddingHorizontalDp.dp - 4.dp,
                top = 11.dp,
                bottom = 12.dp
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(tone)
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    trx(section.title),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = MarbleNativeSpec.SectionLabelTrackingSp.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (section.subtitle.isNotBlank()) {
                    Text(
                        // MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one option, at most one sentence.
                        MarbleCopy.oneSentence(trx(section.subtitle)),
                        color = marbleNativeInkVariant(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        CompositionLocalProvider(LocalSettingsSectionTone provides tone) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                section.content()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

// ---------------------------------------------------------------------------------------------

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

        val statusH = h * 0.22f
        drawRoundRect(
            color = Color(0xFF1B2234),
            topLeft = Offset(pad, pad),
            size = Size(contentW, statusH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        drawCircle(
            color = tone,
            radius = 2.dp.toPx(),
            center = Offset(pad + 5.dp.toPx(), pad + 5.dp.toPx())
        )

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
                val boxY = pad + statusH + 3.dp.toPx()
                val boxH = h * 0.46f
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, boxY),
                    size = Size(contentW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                drawRoundRect(
                    color = tone.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.5f - 14.dp.toPx(), boxY + 3.dp.toPx()),
                    size = Size(28.dp.toPx(), 5.dp.toPx()),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                )

                for (i in 0..2) {
                    val rowY = boxY + 10.dp.toPx() + i * 7.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (i == 0) 0.16f else 0.07f),
                        topLeft = Offset(pad + 3.dp.toPx(), rowY),
                        size = Size(contentW - 6.dp.toPx(), 5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

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
                val boxY = pad + statusH + 3.dp.toPx()
                val boxH = h - boxY - pad
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, boxY),
                    size = Size(contentW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                drawRoundRect(
                    color = tone.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.5f - 14.dp.toPx(), boxY + 3.dp.toPx()),
                    size = Size(28.dp.toPx(), 5.dp.toPx()),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                )

                for (i in 0..3) {
                    val rowY = boxY + 10.dp.toPx() + i * 7.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (i == 0) 0.16f else 0.07f),
                        topLeft = Offset(pad + 3.dp.toPx(), rowY),
                        size = Size(contentW - 6.dp.toPx(), 5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                val fabX = w - pad - 6.dp.toPx()
                val fab1Y = h - pad - 16.dp.toPx()
                val fab2Y = h - pad - 6.dp.toPx()
                drawCircle(color = dangerColor, radius = 4.dp.toPx(), center = Offset(fabX, fab1Y))
                drawCircle(color = tone, radius = 4.dp.toPx(), center = Offset(fabX, fab2Y))
            }

            HomeStyle.IOS_EMBOSSED -> {
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

            HomeStyle.ROUTE_ATELIER -> {
                val cardY = pad + statusH + 3.dp.toPx()
                val cardH = h * 0.40f
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, cardY),
                    size = Size(contentW, cardH),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(pad + 4.dp.toPx(), cardY + 5.dp.toPx()),
                    end = Offset(pad + contentW * 0.55f, cardY + 5.dp.toPx()),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val signetY = cardY + cardH * 0.52f
                drawCircle(color = tone.copy(alpha = 0.35f), radius = 1.7.dp.toPx(), center = Offset(pad + 7.dp.toPx(), signetY))
                drawCircle(color = tone, radius = 1.7.dp.toPx(), center = Offset(pad + contentW * 0.5f, signetY))
                drawCircle(color = tone.copy(alpha = 0.35f), radius = 1.7.dp.toPx(), center = Offset(pad + contentW - 7.dp.toPx(), signetY))
                drawLine(
                    color = tone.copy(alpha = 0.6f),
                    start = Offset(pad + 10.dp.toPx(), signetY),
                    end = Offset(pad + contentW - 10.dp.toPx(), signetY),
                    strokeWidth = 1.dp.toPx()
                )

                drawRoundRect(
                    color = tone,
                    topLeft = Offset(pad + 4.dp.toPx(), cardY + cardH - 9.dp.toPx()),
                    size = Size(contentW - 8.dp.toPx(), 6.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                val lowerY = cardY + cardH + 3.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF181F2E),
                    topLeft = Offset(pad, lowerY),
                    size = Size(contentW, (h - lowerY - pad) * 0.55f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF181F2E).copy(alpha = 0.7f),
                    topLeft = Offset(pad, lowerY + (h - lowerY - pad) * 0.6f),
                    size = Size(contentW, (h - lowerY - pad) * 0.4f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }
}

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
                                HomeStyle.ROUTE_ATELIER -> Aether.Cyan
                            }
                            val shape = RoundedCornerShape(16.dp)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(marbleNativeContainerHigh())
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) tone else marbleNativeOutline(),
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
    }
}

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

                val bandWidth = w * .34f
                val bandX = w * .58f
                drawRoundRect(
                    color = tone.copy(alpha = .34f),
                    topLeft = Offset(bandX - bandWidth / 2f, trackTop),
                    size = Size(bandWidth, r * 2f),
                    cornerRadius = CornerRadius(r, r)
                )

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
                                    AppFont.VAZIR -> "Bundled, real face"
                                    AppFont.SYSTEM -> "The OS default sans"
                                    AppFont.GOOGLE_SANS -> "Renders as system sans"
                                    AppFont.TIMES_NEW_ROMAN -> "Renders as system serif"
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
                trx(
                    "MarbleNG carries no third-party font files: a face your device does not have " +
                        "renders in the closest system face."
                ),
                color = Aether.InkFaint,
                style = settingsBodyStyle()
            )
            Text(
                trx("Persian always shapes with Vazirmatn, whichever Latin face you pick."),
                color = Aether.InkFaint,
                style = settingsBodyStyle()
            )
        }
    }
}

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

private fun coreEngineTitle(engine: CoreEngine): String = when (engine) {
    CoreEngine.XRAY -> "Xray core"
    CoreEngine.SINGBOX -> "sing-box extended"
}

private fun coreEngineDetail(engine: CoreEngine): String = when (engine) {
    CoreEngine.XRAY ->
        "The engine Marble has always run: Xray core behind hev-socks5-tunnel, with every tuning switch in Settings applied to it"
    CoreEngine.SINGBOX ->
        "The extended core: one process owns the tunnel, adds warp, masque and mieru, ships the geo rule sets, and answers URL test from its own measurements"
}

@Composable
private fun CoreEngineOptions(repo: AppRepository, engine: CoreEngine) {
    val s = repo.settings
    val isXray = engine == CoreEngine.XRAY
    val tone = if (isXray) Aether.Emerald else Aether.Amethyst

    Text(
        trx(
            "Every option here is written to the core exactly as you set it, and nothing on this " +
                "page is overridden automatically. " +
                "The JSON box at the bottom wins over the rows."
        ),
        color = Aether.InkMuted,
        style = settingsBodyStyle()
    )
    if (isXray) {
        InformationRow(trx("Fronted by"), "hev-socks5-tunnel ${BuildConfig.HEV_CORE_TAG}", Aether.Amber)
    } else {
        InformationRow(trx("Control API"), trx("127.0.0.1 inside this app; read for URL test only"), Aether.CyanBright)
    }

    Text(trx("Log level"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        val levels = if (isXray) {
            listOf("error", "warning", "info", "debug", "none")
        } else {
            listOf("trace", "debug", "info", "warn", "error", "fatal")
        }
        levels.forEach { level ->
            CyberChoiceChip(
                text = level.uppercase(),
                selected = (if (isXray) s.xrayLogLevel else s.singBoxLogLevel).equals(level, ignoreCase = true),
                color = tone
            ) {
                repo.updateSettings(
                    if (isXray) repo.settings.copy(xrayLogLevel = level)
                    else repo.settings.copy(singBoxLogLevel = level)
                )
            }
        }
    }
    if (isXray) {
        SettingSwitch(
            title = trx("Log DNS answers"),
            subtitle = trx("Ask the core to log every DNS query it resolves • noisy but decisive"),
            checked = s.xrayDnsLog
        ) { repo.updateSettings(repo.settings.copy(xrayDnsLog = it)) }
    } else {
        SettingSwitch(
            title = trx("Timestamp log lines"),
            subtitle = trx("Prefix every line so a report can be lined up with a session"),
            checked = s.singBoxLogTimestamp
        ) { repo.updateSettings(repo.settings.copy(singBoxLogTimestamp = it)) }
    }

    SettingSwitch(
        title = trx("Sniffing"),
        subtitle = trx("Read HTTP/TLS/QUIC so routing sees the real host"),
        checked = if (isXray) s.xraySniffingEnabled else s.singBoxSniffEnabled
    ) {
        repo.updateSettings(
            if (isXray) repo.settings.copy(xraySniffingEnabled = it)
            else repo.settings.copy(singBoxSniffEnabled = it)
        )
    }
    AnimatedVisibility(
        if (isXray) s.xraySniffingEnabled else s.singBoxSniffEnabled,
        enter = expressiveExpandVertically(),
        exit = expressiveCollapseVertically()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (isXray) {
                SettingSwitch(
                    title = trx("Route-only sniffing"),
                    subtitle = trx("Sniff for routing, do not rewrite the destination"),
                    checked = s.xraySniffingRouteOnly
                ) { repo.updateSettings(repo.settings.copy(xraySniffingRouteOnly = it)) }
                SettingSwitch(
                    title = trx("Metadata only"),
                    subtitle = trx("Route from the sniffer's metadata without waiting for the payload"),
                    checked = s.xraySniffMetadataOnly
                ) { repo.updateSettings(repo.settings.copy(xraySniffMetadataOnly = it)) }
                Text(trx("Sniffers, in the order the core applies them"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf("http", "tls", "quic", "fakedns").forEach { sniffer ->
                        val current = s.xraySniffDestOverride.split(',').map { it.trim() }.filter { it.isNotBlank() }
                        CyberChoiceChip(
                            text = sniffer.uppercase(),
                            selected = sniffer in current,
                            color = tone
                        ) {
                            val next = if (sniffer in current) current - sniffer else current + sniffer
                            repo.updateSettings(repo.settings.copy(xraySniffDestOverride = next.joinToString(",")))
                        }
                    }
                }
            } else {
                SettingSwitch(
                    title = trx("Resolve destination"),
                    subtitle = trx("Resolve the domain to an IP before matching routing rules"),
                    checked = s.singBoxResolveDestination
                ) { repo.updateSettings(repo.settings.copy(singBoxResolveDestination = it)) }
                SettingSwitch(
                    title = trx("Override destination"),
                    subtitle = trx("Hand the router the sniffed domain instead of the dialled address"),
                    checked = s.singBoxSniffOverrideDestination
                ) { repo.updateSettings(repo.settings.copy(singBoxSniffOverrideDestination = it)) }
            }
        }
    }

    SettingSwitch(
        title = trx("Allow LAN inbound"),
        subtitle = trx("Bind the inbounds on 0.0.0.0 so other devices can use this phone"),
        checked = if (isXray) s.xrayAllowLan else s.singBoxAllowLan
    ) {
        repo.updateSettings(
            if (isXray) repo.settings.copy(xrayAllowLan = it)
            else repo.settings.copy(singBoxAllowLan = it)
        )
    }
    NumberSetting(
        title = trx("HTTP inbound port"),
        value = if (isXray) s.xrayHttpInboundPort else s.singBoxHttpInboundPort,
        range = 0..65535,
        suffix = if ((if (isXray) s.xrayHttpInboundPort else s.singBoxHttpInboundPort) == 0) " off" else ""
    ) {
        val port = it.coerceIn(0, 65535)
        repo.updateSettings(
            if (isXray) repo.settings.copy(xrayHttpInboundPort = port)
            else repo.settings.copy(singBoxHttpInboundPort = port)
        )
    }
    if (isXray) {
        SettingSwitch(
            title = trx("SOCKS UDP"),
            subtitle = trx("Accept UDP ASSOCIATE on the local SOCKS port"),
            checked = s.xraySocksUdpEnabled
        ) { repo.updateSettings(repo.settings.copy(xraySocksUdpEnabled = it)) }
    } else {
        TinyField(
            label = trx("Inbound username"),
            value = s.singBoxInboundUsername,
            modifier = Modifier.fillMaxWidth(),
            onValue = { repo.updateSettings(repo.settings.copy(singBoxInboundUsername = it.take(64))) }
        )
        TinyField(
            label = trx("Inbound password"),
            value = s.singBoxInboundPassword,
            modifier = Modifier.fillMaxWidth(),
            onValue = { repo.updateSettings(repo.settings.copy(singBoxInboundPassword = it.take(64))) }
        )
        if (s.singBoxInboundUsername.isNotBlank() && s.singBoxInboundPassword.isBlank()) {
            Text(
                trx("A username without a password is not written: the core needs both or neither."),
                color = Aether.Amber,
                style = settingsBodyStyle()
            )
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    Text(trx("Multiplexing"), color = Aether.Ink, style = MaterialTheme.typography.titleSmall)
    if (isXray) {
        SettingSwitch(
            title = trx("Mux.Cool"),
            subtitle = trx("Carry many streams over one connection to this node"),
            checked = s.muxEnabled
        ) { repo.updateSettings(repo.settings.copy(muxEnabled = it)) }
        AnimatedVisibility(
            s.muxEnabled,
            enter = expressiveExpandVertically(),
            exit = expressiveCollapseVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                NumberSetting(
                    title = trx("Concurrency"),
                    value = s.muxConcurrency,
                    range = 1..128
                ) { repo.updateSettings(repo.settings.copy(muxConcurrency = it.coerceIn(1, 128))) }
                NumberSetting(
                    title = trx("UDP concurrency"),
                    value = s.muxXudpConcurrency,
                    range = 0..128,
                    suffix = if (s.muxXudpConcurrency == 0) " off" else ""
                ) { repo.updateSettings(repo.settings.copy(muxXudpConcurrency = it.coerceIn(0, 128))) }
                Text(trx("UDP to port 443"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    XrayMuxUdp443Modes.ALL.forEach { mode ->
                        CyberChoiceChip(
                            text = mode.uppercase(),
                            selected = s.muxUdp443.equals(mode, ignoreCase = true),
                            color = tone
                        ) { repo.updateSettings(repo.settings.copy(muxUdp443 = mode)) }
                    }
                }
            }
        }
    } else {
        SettingSwitch(
            title = trx("Multiplex"),
            subtitle = trx("Carry many streams over one connection to this node"),
            checked = s.singBoxMuxEnabled
        ) { repo.updateSettings(repo.settings.copy(singBoxMuxEnabled = it)) }
        AnimatedVisibility(
            s.singBoxMuxEnabled,
            enter = expressiveExpandVertically(),
            exit = expressiveCollapseVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(trx("Protocol"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    SingBoxMuxProtocols.ALL.forEach { protocol ->
                        CyberChoiceChip(
                            text = protocol.uppercase(),
                            selected = s.singBoxMuxProtocol.equals(protocol, ignoreCase = true),
                            color = tone
                        ) { repo.updateSettings(repo.settings.copy(singBoxMuxProtocol = protocol)) }
                    }
                }
                NumberSetting(
                    title = trx("Max connections"),
                    value = s.singBoxMuxMaxConnections,
                    range = 1..128
                ) { repo.updateSettings(repo.settings.copy(singBoxMuxMaxConnections = it.coerceIn(1, 128))) }
                NumberSetting(
                    title = trx("Min streams"),
                    value = s.singBoxMuxMinStreams,
                    range = 0..128
                ) { repo.updateSettings(repo.settings.copy(singBoxMuxMinStreams = it.coerceIn(0, 128))) }
                NumberSetting(
                    title = trx("Max streams"),
                    value = s.singBoxMuxMaxStreams,
                    range = 1..1024
                ) { repo.updateSettings(repo.settings.copy(singBoxMuxMaxStreams = it.coerceIn(1, 1024))) }
                SettingSwitch(
                    title = trx("Padding"),
                    subtitle = trx("Pad multiplexed frames so their size stops being a signature"),
                    checked = s.singBoxMuxPadding
                ) { repo.updateSettings(repo.settings.copy(singBoxMuxPadding = it)) }
            }
        }
        Text(
            trx("REALITY and XTLS-Vision nodes negotiate their own flow control; multiplexing on top of them is slower, so the writers leave it off for those nodes and say so."),
            color = Aether.InkFaint,
            style = settingsBodyStyle()
        )
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    SettingSwitch(
        title = trx("TCP Fast Open"),
        subtitle = trx("Send data with the handshake • applies to both cores"),
        checked = s.tcpFastOpenEnabled
    ) { repo.updateSettings(repo.settings.copy(tcpFastOpenEnabled = it)) }

    if (isXray) {
        SettingSwitch(
            title = trx("Disable Nagle"),
            subtitle = trx("tcpNoDelay on every dial the core makes"),
            checked = s.xrayTcpNoDelay
        ) { repo.updateSettings(repo.settings.copy(xrayTcpNoDelay = it)) }
        SettingSwitch(
            title = trx("Multipath TCP"),
            subtitle = trx("Offer MPTCP to the server, with a guaranteed plain-TCP fallback"),
            checked = s.xrayTcpMptcp
        ) { repo.updateSettings(repo.settings.copy(xrayTcpMptcp = it)) }
        NumberSetting(
            title = trx("Keep-alive interval"),
            value = s.xrayTcpKeepAliveIntervalSec,
            range = 0..600,
            suffix = if (s.xrayTcpKeepAliveIntervalSec == 0) " core default" else "s"
        ) { repo.updateSettings(repo.settings.copy(xrayTcpKeepAliveIntervalSec = it.coerceIn(0, 600))) }
        TinyField(
            label = trx("User timeout (ms, 0 = core default)"),
            value = s.xrayTcpUserTimeoutMs.takeIf { it > 0 }?.toString() ?: "",
            modifier = Modifier.fillMaxWidth(),
            onValue = { text ->
                val ms = text.filter(Char::isDigit).take(6).toIntOrNull() ?: 0
                repo.updateSettings(repo.settings.copy(xrayTcpUserTimeoutMs = ms.coerceIn(0, 600_000)))
            }
        )
        TinyField(
            label = trx("Window clamp (bytes, 0 = off)"),
            value = s.xrayTcpWindowClamp.takeIf { it > 0 }?.toString() ?: "",
            modifier = Modifier.fillMaxWidth(),
            onValue = { text ->
                val clamp = text.filter(Char::isDigit).take(6).toIntOrNull() ?: 0
                repo.updateSettings(repo.settings.copy(xrayTcpWindowClamp = clamp.coerceIn(0, 999_999)))
            }
        )
        TinyField(
            label = trx("TCP congestion control (blank = core default)"),
            value = s.xrayTcpCongestion,
            modifier = Modifier.fillMaxWidth(),
            onValue = { repo.updateSettings(repo.settings.copy(xrayTcpCongestion = it.trim().take(32))) }
        )
        Text(trx("Dial address family"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            XrayDomainStrategies.ALL.forEach { strategy ->
                CyberChoiceChip(
                    text = strategy.uppercase(),
                    selected = s.xraySockoptDomainStrategy.equals(strategy, ignoreCase = true),
                    color = tone
                ) { repo.updateSettings(repo.settings.copy(xraySockoptDomainStrategy = strategy)) }
            }
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    if (isXray) {
        Text(trx("Routing"), color = Aether.Ink, style = MaterialTheme.typography.titleSmall)

        Text(
            trx("Domain strategy and matcher stay on the Routing page, next to the rules they apply to."),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall
        )

        Text(trx("Policy (seconds, 0 = core default)"), color = Aether.Ink, style = MaterialTheme.typography.titleSmall)
        NumberSetting(
            title = trx("Handshake"),
            value = s.xrayPolicyHandshakeSec,
            range = 0..600,
            suffix = if (s.xrayPolicyHandshakeSec == 0) " core default" else "s"
        ) { repo.updateSettings(repo.settings.copy(xrayPolicyHandshakeSec = it.coerceIn(0, 600))) }
        NumberSetting(
            title = trx("Connection idle"),
            value = s.xrayPolicyConnIdleSec,
            range = 0..3600,
            suffix = if (s.xrayPolicyConnIdleSec == 0) " core default" else "s"
        ) { repo.updateSettings(repo.settings.copy(xrayPolicyConnIdleSec = it.coerceIn(0, 3_600))) }
        NumberSetting(
            title = trx("Uplink only"),
            value = s.xrayPolicyUplinkOnlySec,
            range = 0..600,
            suffix = if (s.xrayPolicyUplinkOnlySec == 0) " core default" else "s"
        ) { repo.updateSettings(repo.settings.copy(xrayPolicyUplinkOnlySec = it.coerceIn(0, 600))) }
        NumberSetting(
            title = trx("Downlink only"),
            value = s.xrayPolicyDownlinkOnlySec,
            range = 0..600,
            suffix = if (s.xrayPolicyDownlinkOnlySec == 0) " core default" else "s"
        ) { repo.updateSettings(repo.settings.copy(xrayPolicyDownlinkOnlySec = it.coerceIn(0, 600))) }
        NumberSetting(
            title = trx("Buffer size"),
            value = s.xrayPolicyBufferSizeKb,
            range = 0..1024,
            suffix = if (s.xrayPolicyBufferSizeKb == 0) " core default" else " KB"
        ) { repo.updateSettings(repo.settings.copy(xrayPolicyBufferSizeKb = it.coerceIn(0, 1024))) }

        SettingSwitch(
            title = trx("Maximum config compatibility"),
            subtitle = trx("Verify the final config with Xray before connecting"),
            checked = s.configCompatibilityMode
        ) { repo.updateSettings(repo.settings.copy(configCompatibilityMode = it)) }

        SettingSwitch(
            title = trx("Dial unencrypted nodes"),
            subtitle = trx("Plaintext VLESS/Trojan nodes stay usable; switching off refuses them"),
            checked = s.allowUnencryptedPublicOutbound
        ) { repo.updateSettings(repo.settings.copy(allowUnencryptedPublicOutbound = it)) }
        Text(
            trx(
                "Unencrypted traffic is readable by your ISP, and Marble labels it on the " +
                    "server row without rewriting your node."
            ),
            color = Aether.InkMuted,
            style = settingsBodyStyle()
        )
    } else {
        SettingSwitch(
            title = trx("Prefer link parser"),
            subtitle = trx("Hand the original link to the extended core's own parser."),
            checked = s.singBoxPreferParser,
            onChecked = { repo.updateSettings(repo.settings.copy(singBoxPreferParser = it)) }
        )
        SettingSwitch(
            title = trx("Unified delay"),
            subtitle = trx("Measure a real round trip instead of trusting a cached handshake."),
            checked = s.singBoxUnifiedDelay,
            onChecked = { repo.updateSettings(repo.settings.copy(singBoxUnifiedDelay = it)) }
        )
        SettingSwitch(
            title = trx("Cache file"),
            subtitle = trx("Remember resolved addresses and DNS answers between runs."),
            checked = s.singBoxCacheFile,
            onChecked = { repo.updateSettings(repo.settings.copy(singBoxCacheFile = it)) }
        )
        NumberSetting(
            title = trx("Connect timeout"),
            value = s.singBoxConnectTimeoutSec,
            range = 3..60,
            suffix = "s",
            onValue = { repo.updateSettings(repo.settings.copy(singBoxConnectTimeoutSec = it.coerceIn(3, 60))) }
        )
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    Text(
        trx("Extra JSON (advanced)"),
        color = Aether.Ink,
        style = MaterialTheme.typography.titleSmall
    )
    OutlinedTextField(
        value = if (isXray) s.xrayExtraJson else s.singBoxExtraJson,
        onValueChange = { text ->
            repo.updateSettings(
                if (isXray) repo.settings.copy(xrayExtraJson = text)
                else repo.settings.copy(singBoxExtraJson = text)
            )
        },
        label = {
            Text(
                trx(
                    if (isXray) "Merged into the Xray config"
                    else "Merged into the sing-box config"
                )
            )
        },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        minLines = 6,
        maxLines = 18,
        shape = RoundedCornerShape(17.dp),
        colors = marbleOutlinedTextFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

    val ownedBlocks = if (isXray) "inbounds, outbounds, routing, dns, log"
    else "inbounds, outbounds, route, dns, log"
    Text(
        trx(
            "Anything the rows above do not cover — stats, api, metrics, observatory, reverse, a " +
                "per-outbound sockopt — belongs here. " +
                "A key the app owns ($ownedBlocks) is refused as a whole with the reason."
        ),
        color = Aether.InkFaint,
        style = settingsBodyStyle()
    )
}

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

            InformationRow("sing-box core", BuildConfig.SINGBOX_CORE_TAG, Aether.CyanBright)
            InformationRow("Build type", BuildConfig.BUILD_TYPE, Aether.InkMuted)
        }

        SettingsHubCard(
            title = "Bug Finder",
            subtitle = "Scan the runtime, then copy or save the report",
            tone = Aether.Danger
        ) {
            BugFinderSettings(repo)
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
            InformationLinkRow(
                title = "sing-box core",
                subtitle = "https://github.com/${BuildConfig.SINGBOX_CORE_REPO}",
                tone = Aether.CyanBright,
                onClick = {
                    openExternal(context, "https://github.com/${BuildConfig.SINGBOX_CORE_REPO}")
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
                                "hev-socks5-tunnel ${BuildConfig.HEV_CORE_TAG}\n" +
                                "sing-box ${BuildConfig.SINGBOX_CORE_TAG}"
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
    val shape = marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MarbleNativeSpec.TouchTargetDp.dp)
            .clip(shape)
            .background(marbleNativeCardFill())
            .border(1.dp, marbleNativeOutline(), shape)
            .padding(
                horizontal = MarbleNativeSpec.RowPaddingHorizontalDp.dp - 4.dp,
                vertical = 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            trx(label),
            color = Aether.Ink,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(9.dp))
        Text(
            value,
            color = tone,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
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
    val shape = marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MarbleNativeSpec.RowTwoLineHeightDp.dp)
            .clip(shape)
            .background(marbleNativeCardFill())
            .border(1.dp, marbleNativeOutline(), shape)
            .kineticClickable(role = Role.Button, boundedShape = shape, showIndication = false, onClick = onClick)
            .padding(
                horizontal = MarbleNativeSpec.RowPaddingHorizontalDp.dp - 4.dp,
                vertical = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                trx(title),
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
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
        MarbleNativeChevron(marbleNativeOutline().copy(alpha = .9f))
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
    var page by rememberSaveable {
        mutableStateOf(repo.lastSettingsPage.ifBlank { SettingsPages.HUB })
    }

    val hubListState = rememberLazyListState()
    val themeListState = rememberLazyListState()
    val homeStyleListState = rememberLazyListState()
    val typefaceListState = rememberLazyListState()
    val languageListState = rememberLazyListState()
    val informationListState = rememberLazyListState()
    val routingListState = rememberLazyListState()

    val dockSlotListState = rememberLazyListState()

    val workspaceListStates = remember {
        SettingsWorkspaceTab.entries.associateWith { LazyListState() }
    }
    fun workspaceListState(tab: SettingsWorkspaceTab): LazyListState =
        workspaceListStates.getValue(tab)

    var hubCollapsed by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(page) {
        repo.rememberSettingsPage(page)
    }

    LaunchedEffect(focusSection) {
        if (focusSection == "Routing") {
            page = SettingsPages.ROUTING
        } else if (focusSection == SettingsPages.TESTS) {
            page = SettingsPages.workspace(SettingsWorkspaceTab.TESTS, SettingsPages.TESTS_CARD)
        } else if (focusSection == SettingsPages.DOCK_SLOT) {
            page = SettingsPages.DOCK_SLOT
        }
    }

    val settingsBackProgress = remember { MutableStateFlow(0f) }
    val settingsBack by settingsBackProgress.collectAsState()
    PredictiveBackHandler(enabled = page != SettingsPages.HUB) { events ->
        try {
            events.collect { event -> settingsBackProgress.value = event.progress }
            page = SettingsPages.HUB
        } catch (cancelled: CancellationException) {
        } finally {
            settingsBackProgress.value = 0f
        }
    }

    AnimatedContent(
        targetState = page,
        modifier = Modifier
            .fillMaxSize()
            .marblePredictiveBack(settingsBack),
        transitionSpec = {
            // MARBLE_STABLE_NAVIGATION_V202 — settings pages all own large scrolling headers.

            expressiveFadeThrough()
        },
        label = "settings-page"
    ) { target ->
        when {
            target == SettingsPages.HUB -> SettingsHub(
                repo = repo,
                onNavigate = { page = it },
                listState = hubListState,
                collapsed = hubCollapsed,
                onToggleGroup = { key -> hubCollapsed = SettingsHubPolicy.toggled(hubCollapsed, key) },
                onExpandAll = { hubCollapsed = SettingsHubPolicy.expandAll() },
                onCollapseAll = { hubCollapsed = SettingsHubPolicy.collapseAll(SettingsHubGroups.Keys) }
            )

            SettingsPages.isCard(target) -> SettingsCardPage(
                repo = repo,
                tab = SettingsPages.cardTab(target),
                cardTitle = SettingsPages.cardTitle(target),
                onBack = { page = SettingsPages.HUB },
                onNavigate = { page = it }
            )

            SettingsPages.isWorkspace(target) -> SettingsTabPage(
                repo = repo,
                tab = SettingsPages.workspaceTab(target),
                focusSection = SettingsPages.workspaceFocus(target),
                listState = workspaceListState(SettingsPages.workspaceTab(target)),
                onBack = { page = SettingsPages.HUB },
                onNavigate = { page = it }
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

            target == SettingsPages.ROUTING ->
                SettingsRoutingPage(
                    repo = repo,
                    listState = routingListState,
                    onBack = { page = SettingsPages.HUB }
                )

            target == SettingsPages.DOCK_SLOT ->
                SettingsDockSlotPage(
                    repo = repo,
                    listState = dockSlotListState,
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
    onNavigate: (String) -> Unit = {},
    focusSection: String? = null,
    listState: LazyListState = rememberLazyListState()
) {
    val sections = settingsSections(tab, repo, focusSection, onNavigate)

    val focusIsFirstCard = focusSection != null &&
        sections.firstOrNull()?.title?.contains(focusSection, ignoreCase = true) == true
    if (focusIsFirstCard) {
        LaunchedEffect(focusSection, tab) { listState.scrollToItem(0) }
    }
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

private fun settingsTabPageTitle(tab: SettingsWorkspaceTab): String = when (tab) {
    SettingsWorkspaceTab.GENERAL -> "General"
    SettingsWorkspaceTab.TESTS -> "Tests & ranking"
    SettingsWorkspaceTab.NETWORK -> "Network & routing"
    SettingsWorkspaceTab.ENGINE -> "Engine & tunnel"
    SettingsWorkspaceTab.SYSTEM -> "System"
}

private fun settingsTabPageSubtitle(tab: SettingsWorkspaceTab): String = when (tab) {
    SettingsWorkspaceTab.GENERAL -> "Home layout, sources and app updates"
    SettingsWorkspaceTab.TESTS -> "Probes, ranking and live route intelligence"
    SettingsWorkspaceTab.NETWORK -> "DNS, split tunnel and geo rules"
    SettingsWorkspaceTab.ENGINE -> "Xray, transport and adaptive buffers"
    SettingsWorkspaceTab.SYSTEM -> "Notifications and background access"
}

private class SettingsSectionSpec(
    val title: String,
    val subtitle: String,
    val icon: HomeIcon,
    val color: Color,
    val content: @Composable () -> Unit
)

@Composable
private fun settingsSections(
    tab: SettingsWorkspaceTab,
    repo: AppRepository,
    focusSection: String?,
    onNavigate: (String) -> Unit = {}
): List<SettingsSectionSpec> {
    fun card(
        title: String,
        subtitle: String,
        icon: HomeIcon,
        color: Color,
        content: @Composable () -> Unit
    ): SettingsSectionSpec = SettingsSectionSpec(title, subtitle, icon, color, content)

    return when (tab) {
        SettingsWorkspaceTab.GENERAL -> listOf(
            card("Connection","Tunnel, proxy, port",HomeIcon.TUNNEL,Aether.Cyan) { ConnectionSettings(repo) },

            card(
                "Navigation bar",
                dockSettingsSubtitle(repo.settings),
                HomeIcon.SERVER,
                Aether.Emerald
            ) { DockSettings(repo) },

            card("Subscriptions","Refresh & sources",HomeIcon.LIBRARY,Aether.Amethyst) { SubscriptionSettings(repo) },

            card(
                "Server cards",
                serverLayoutSubtitle(repo.settings),
                HomeIcon.SERVER,
                Aether.Amethyst
            ) { ServerLayoutChoice(repo) }
        )

        SettingsWorkspaceTab.TESTS -> listOf(
            card(
                "Testing",

                "Ping method • ${probeMethodShortLabel(repo.settings.probeMethod)}",
                HomeIcon.BENCHMARK,
                Aether.Amethyst
            ) { ProbeSettings(repo) },

            card(
                "Automatic server selector",
                if (repo.settings.autoServerSelectorEnabled) {
                    "On • ${AutoServerSelector.shortLabel(repo.settings.autoServerStrategyEnum)}"
                } else {
                    "Off • you choose the server"
                },
                HomeIcon.RANK,
                Aether.Cyan
            ) { AutoServerSelectorSettings(repo) },
            card("Identity Guard","Keep one public exit",HomeIcon.SHIELD,Aether.Cyan) {
                SettingSwitch(
                    title = "Identity Guard",
                    subtitle = "Pin the session to one exit IP",
                    checked = repo.settings.identityGuardEnabled
                ) { repo.updateSettings(repo.settings.copy(identityGuardEnabled = it)) }
            }
        )
        SettingsWorkspaceTab.NETWORK -> listOf(

            card("Routing","Open the dedicated routing workspace",HomeIcon.ROUTING,Aether.Emerald) {
                RoutingEntryCard(repo) { onNavigate(SettingsPages.ROUTING) }
            },
            card("DNS","TUN & DoH",HomeIcon.NETWORK,Aether.Cyan) { DnsSettings(repo) },

            card("Per-app proxy","Tunnel or bypass per app",HomeIcon.PRIVACY,Aether.Emerald) { SplitTunnelSettings(repo) }
        )
        SettingsWorkspaceTab.ENGINE -> {
            val engine = parseCoreEngine(repo.settings.coreEngineId)
            listOf(

                card("Engine",CoreEngineInfo.displayName(engine) + " • Switching closes the tunnel; reconnect to run the new core",HomeIcon.TUNNEL,Aether.Cyan) {
                    CoreEngine.entries.forEach { candidate ->
                        val selected = engine == candidate
                        val tone = if (candidate == CoreEngine.XRAY) Aether.Emerald else Aether.Amethyst
                        val shape = RoundedCornerShape(14.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(marbleNativeContainerHigh())
                                .border(
                                    1.dp,
                                    if (selected) tone.copy(alpha = .58f) else marbleNativeOutline(),
                                    shape
                                )
                                .kineticClickable(role = Role.Button, boundedShape = shape) {
                                    repo.setCoreEngine(candidate)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    trx(coreEngineTitle(candidate)),
                                    color = if (selected) tone else Aether.Ink,
                                    style = settingsRowTitleStyle(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    trx(coreEngineDetail(candidate)),
                                    color = Aether.InkFaint,
                                    style = settingsBodyStyle(),
                                    maxLines = 3,
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
                },
                card("Pinned versions","Read from core-lock.json at build time",HomeIcon.DETAILS,Aether.Emerald) {
                    InformationRow(trx("Xray core"), BuildConfig.XRAY_CORE_TAG, Aether.Emerald)
                    InformationRow(trx("Tunnel core"), BuildConfig.HEV_CORE_TAG, Aether.Amber)
                    InformationRow(trx("sing-box extended"), BuildConfig.SINGBOX_CORE_TAG, Aether.CyanBright)
                    InformationRow(
                        trx("Running engine"),
                        CoreEngineInfo.displayName(repo.activeCoreEngine),
                        if (repo.activeCoreEngine == CoreEngine.SINGBOX) Aether.Amethyst else Aether.Emerald
                    )
                    if (repo.coreStartPhase.isNotBlank()) {
                        InformationRow(trx("Start phase"), repo.coreStartPhase, Aether.InkMuted)
                    }
                    if (repo.coreStartError.isNotBlank()) {
                        InformationRow(trx("Last start error"), repo.coreStartError, Aether.Danger)
                    }
                },

                card(
                    "Core options",
                    "Sniffing, LAN, log • ${CoreEngineInfo.displayName(engine)}",
                    HomeIcon.SHIELD,
                    Aether.Emerald
                ) { CoreEngineOptions(repo, engine) },
                card("Delay test","What a real-delay measurement loads",HomeIcon.PING,Aether.Amber) {
                    TinyField(
                        label = trx("Delay test URL"),
                        value = repo.settings.delayTestUrl,
                        onValue = { repo.setDelayTestUrl(it) }
                    )
                    Text(
                        trx("The https address Real delay opens through the tunnel and times."),
                        color = Aether.InkFaint,
                        style = settingsBodyStyle()
                    )
                    if (repo.settings.delayTestUrl.trim() != DelayTest.URL) {
                        CyberButton(
                            label = trx("Reset to default"),
                            color = Aether.Cyan,
                            modifier = Modifier.fillMaxWidth()
                        ) { repo.setDelayTestUrl(DelayTest.URL) }
                    }
                },
            )
        }

        SettingsWorkspaceTab.SYSTEM -> listOf(
            card("Notifications","Alerts",HomeIcon.STATUS,Aether.Cyan) { NotificationSettings(repo) },

            card("Background access","Fixes interrupted VPN in background",HomeIcon.POWER,Aether.Amber) {
                BackgroundAccessSettings(repo)
            }
        )
    }
}

@Composable
private fun SettingsCardPage(
    repo: AppRepository,
    tab: SettingsWorkspaceTab,
    cardTitle: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val spec = settingsSections(tab, repo, null, onNavigate).firstOrNull { it.title == cardTitle }
    SettingsSubPage(
        title = spec?.title ?: cardTitle,
        subtitle = spec?.subtitle.orEmpty(),
        onBack = onBack,
        listState = rememberLazyListState()
    ) {
        if (spec == null) {
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
            PrismPanel(
                modifier = Modifier.fillMaxWidth(),
                accent = spec.color,
                contentPadding = PaddingValues(16.dp),
                verticalSpacing = MarbleSpacing.S
            ) {
                spec.content()
            }
        }
    }
}

@Composable
private fun TextScaleChoice(repo: AppRepository) {
    val current = TextScale.clamp(repo.settings.textScalePercent)
    val tone = Aether.Amber
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                trx("Text size"),
                color = Aether.Ink,
                style = settingsRowTitleStyle(),
                modifier = Modifier.weight(1f)
            )
            Text(
                "$current%",
                color = tone,
                style = settingsRowTitleStyle()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextScale.STEPS.forEach { step ->
                val selected = step == current
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(
                            if (selected) tone.copy(alpha = .18f) else Aether.Glass.copy(alpha = .42f)
                        )
                        .border(
                            1.dp,
                            if (selected) tone.copy(alpha = .58f) else Aether.GlassBorderSoft.copy(alpha = .5f),
                            shape
                        )
                        .kineticClickable(role = Role.Button, boundedShape = shape) {
                            repo.updateSettings(repo.settings.copy(textScalePercent = step))
                        }
                        .padding(vertical = 10.dp)
                        .semantics { contentDescription = "Text size $step%" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$step%",
                        color = if (selected) tone else Aether.InkMuted,
                        style = settingsBodyStyle(),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            trx("Applies to every screen, and takes effect at once."),
            color = Aether.InkFaint,
            style = settingsBodyStyle()
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
    PrismPanel(
        modifier=Modifier.fillMaxWidth(),
        accent=color,
        contentPadding=PaddingValues(16.dp),
        verticalSpacing=MarbleSpacing.S
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
                    Modifier.size(19.dp)
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
                        // MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one option, at most one sentence.
                        MarbleCopy.oneSentence(trx(subtitle)),
                        color=Aether.InkMuted,
                        style=MaterialTheme.typography.bodySmall,
                        maxLines=1,
                        overflow=TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        CompositionLocalProvider(LocalSettingsSectionTone provides color) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun homeStyleLabel(style: HomeStyle): String = when (style) {
    HomeStyle.IOS_SLIDER -> Tr.now.styleIosSlider
    HomeStyle.IOS_FLOATING -> Tr.now.styleIosFloating
    HomeStyle.IOS_EMBOSSED -> Tr.now.styleIosEmbossed
    HomeStyle.IOS_MODULAR -> Tr.now.styleIosModular
    HomeStyle.ROUTE_ATELIER -> Tr.now.styleRouteAtelier
}

@Composable
private fun homeStyleDetail(style: HomeStyle): String = when (style) {
    HomeStyle.IOS_SLIDER -> Tr.now.styleIosSliderDetail
    HomeStyle.IOS_FLOATING -> Tr.now.styleIosFloatingDetail
    HomeStyle.IOS_EMBOSSED -> Tr.now.styleIosEmbossedDetail
    HomeStyle.IOS_MODULAR -> Tr.now.styleIosModularDetail
    HomeStyle.ROUTE_ATELIER -> Tr.now.styleRouteAtelierDetail
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
private fun NotificationSettings(repo: AppRepository) {
    val context = LocalContext.current
    val s = repo.settings
    fun hasRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun notificationAccessGranted(): Boolean =
        hasRuntimePermission() &&
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

    var permissionGranted by remember { mutableStateOf(notificationAccessGranted()) }
    val needsRuntimePermission = Build.VERSION.SDK_INT >= 33 && !hasRuntimePermission()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionGranted = notificationAccessGranted()
    }
    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionGranted = notificationAccessGranted()
    }
    val permissionTone = if (permissionGranted) Aether.Emerald else Aether.Amber

    PrismPanel(
        modifier = Modifier.fillMaxWidth(),
        accent = permissionTone,
        contentPadding = PaddingValues(12.dp),
        verticalSpacing = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(permissionTone.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(HomeIcon.STATUS, permissionTone, Modifier.size(19.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    trx("Notification access"),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    trx(
                        when {
                            permissionGranted -> "Allowed by Android"
                            needsRuntimePermission -> "Allow Android notifications to show alerts and live status"
                            else -> "Disabled in Android settings"
                        }
                    ),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HoloBadge(
                if (permissionGranted) "ALLOWED" else "ACTION NEEDED",
                permissionTone,
                compact = true
            )
        }
        if (needsRuntimePermission) {
            CyberButton(
                label = "Allow notifications",
                color = Aether.Amber,
                icon = HomeIcon.STATUS,
                modifier = Modifier.fillMaxWidth(),
                compact = true
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        CyberButton(
            label = "Send test",
            color = Aether.Cyan,
            icon = HomeIcon.PING,
            modifier = Modifier.weight(1f),
            enabled = permissionGranted && s.smartNotificationsEnabled,
            compact = true
        ) { repo.testSmartNotification() }
        CyberButton(
            label = "Notification settings",
            color = Aether.Amethyst,
            icon = HomeIcon.STATUS,
            modifier = Modifier.weight(1f),
            compact = true
        ) {
            systemSettingsLauncher.launch(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }
    CyberButton(
        label = "Clear optional alerts",
        color = Aether.InkMuted,
        variant = PrismButtonVariant.Quiet,
        modifier = Modifier.fillMaxWidth(),
        compact = true
    ) { repo.clearSmartNotifications() }

    SettingSwitch(
        title = "Live connection telemetry",
        subtitle = "Ping and rates in the ongoing notification",
        checked = s.notificationLiveStats
    ) { repo.updateSettings(s.copy(notificationLiveStats = it)) }

    SettingSwitch(
        title = "Optional event alerts",
        subtitle = "",
        checked = s.smartNotificationsEnabled
    ) { repo.updateSettings(s.copy(smartNotificationsEnabled = it)) }

    AnimatedVisibility(
        s.smartNotificationsEnabled,
        enter = expressiveExpandVertically(),
        exit = expressiveCollapseVertically()
    ) {
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
                subtitle = "Kill switch and blocked routes",
                checked = s.notifyPrivacyWarnings
            ) { repo.updateSettings(repo.settings.copy(notifyPrivacyWarnings = it)) }
            SettingSwitch(
                title = "Network changes",
                subtitle = "Wi-Fi and cellular switches",
                checked = s.notifyNetworkChanges
            ) { repo.updateSettings(repo.settings.copy(notifyNetworkChanges = it)) }
            SettingSwitch(
                title = "Subscription updates",
                subtitle = "Refresh results and failures",
                checked = s.notifySubscriptionEvents
            ) { repo.updateSettings(repo.settings.copy(notifySubscriptionEvents = it)) }
            SettingSwitch(
                title = "Core updates",
                subtitle = "Xray and HEV core releases",
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
private fun BackgroundAccessSettings(repo: AppRepository) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(repo.backgroundExemptionGranted()) }
    val returnFromAndroidPage = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        granted = repo.backgroundExemptionGranted()
    }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        HoloBadge(
            if (granted) "UNRESTRICTED ON" else "BACKGROUND RESTRICTED",
            if (granted) Aether.Emerald else Aether.Amber,
            compact = true
        )
    }

    if (granted) {
        Text(
            trx("Unrestricted background activity is already granted, so the tunnel may stay alive with the screen off."),
            color = Aether.InkMuted,
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Text(
            trx("One tap opens Android's own page to grant unrestricted background activity."),
            color = Aether.InkMuted,
            style = MaterialTheme.typography.bodySmall
        )
        CyberButton(
            label = "Grant unrestricted background access",
            color = Aether.Amber,
            modifier = Modifier.fillMaxWidth(),
            icon = HomeIcon.POWER
        ) {
            repo.noteBackgroundAccessRequest()
            val direct = repo.unrestrictedBackgroundDirectIntent()
            val fallback = repo.unrestrictedBackgroundFallbackIntent()
            runCatching {
                returnFromAndroidPage.launch(direct)
            }.recoverCatching {
                returnFromAndroidPage.launch(fallback)
            }.onFailure {
                repo.requestUnrestrictedBackgroundAccess(context)
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
                repo.serverIntelLoading -> MarbleExpressiveCircularIndicator(
                    modifier=Modifier.size(22.dp),
                    color=Aether.Cyan,
                    strokeWidth=2.dp,
                    arcCount=3
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
                            HomeVectorIcon(HomeIcon.DETAILS, Aether.Cyan, Modifier.size(18.dp))
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
                        HomeVectorIcon(HomeIcon.DETAILS, Aether.Cyan.copy(alpha = .55f), Modifier.size(15.dp))
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
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(trx(if(apps.isEmpty())"Loading installed apps…" else "${visibleApps.size} apps"),color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall);HoloBadge("${selected.size} selected",Aether.Emerald,true)}
        LazyColumn(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(18.dp)).background(Aether.Glass.copy(alpha=.70f)),contentPadding=PaddingValues(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(2.dp),userScrollEnabled=true){items(visibleApps,key={it.packageName}){app->SplitTunnelAppRow(app,app.packageName in selected){toggle(app.packageName)}}}
        Text(trx("Applies on next Full TUN connect."),color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SplitTunnelAppRow(app:InstalledApp,checked:Boolean,onToggle:()->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).kineticClickable(role=Role.Checkbox,onClick=onToggle).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if(checked)Aether.Emerald.copy(alpha=.12f) else Aether.GlassStrong),contentAlignment=Alignment.Center){Text(app.label.trim().firstOrNull()?.uppercase()?:"•",color=if(checked)Aether.Emerald else Aether.InkMuted,style=MaterialTheme.typography.labelLarge)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(app.label,color=Aether.Ink,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(app.packageName,color=Aether.InkFaint,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Checkbox(checked,{onToggle()},colors=CheckboxDefaults.colors(checkedColor=Aether.Emerald,checkmarkColor=Aether.Void,uncheckedColor=Aether.GlassBorder))}
}

private fun serverLayoutSubtitle(settings: AppSettings): String =
    if (settings.serversLayoutEnum == ServerLayout.GRID) {
        "Compact boxes • several servers per line"
    } else {
        "Rows • one server per line"
    }

@Composable
private fun serverLayoutLabel(layout: ServerLayout): String = when (layout) {
    ServerLayout.ROW -> trx("Rows")
    ServerLayout.GRID -> trx("Compact boxes")
}

@Composable
private fun ServerLayoutChoice(repo: AppRepository) {
    val current = repo.settings.serversLayoutEnum
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ServerLayout.entries.forEach { layout ->
            val selected = current == layout
            val shape = RoundedCornerShape(13.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (selected) Aether.Amethyst.copy(alpha = .10f) else homeCloudInsetFill())
                    .border(
                        1.dp,
                        if (selected) Aether.Amethyst.copy(alpha = .40f) else homeCloudInsetBorder(),
                        shape
                    )
                    .kineticClickable(role = Role.RadioButton, boundedShape = shape) {
                        repo.updateSettings(repo.settings.copy(serversLayout = layout.id))
                    }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ServerLayoutPreview(layout = layout, tone = Aether.Amethyst, active = selected)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        serverLayoutLabel(layout),
                        color = if (selected) Aether.Amethyst else Aether.Ink,
                        style = settingsRowTitleStyle(),
                        maxLines = 1
                    )
                    Text(
                        // MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one choice, one sentence.
                        MarbleCopy.oneSentence(
                            trx(serverLayoutDetail(layout))
                        ),
                        color = Aether.InkMuted,
                        style = settingsBodyStyle(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (selected) Aether.Amethyst else Aether.InkFaint.copy(alpha = .30f))
                )
            }
        }
    }
}

@Composable
private fun serverLayoutDetail(layout: ServerLayout): String = when (layout) {
    ServerLayout.ROW -> "One server per line, with its full address and actions"
    ServerLayout.GRID -> "Small cards, as many per line as your screen fits"
}

// ---------------------------------------------------------------------------------------------

// ---------------------------------------------------------------------------------------------

private enum class HomePingSurface { GAUGE, CHIP, HEADER }

@Composable
private fun homePingSurfaceTitle(surface: HomePingSurface): String = when (surface) {
    HomePingSurface.GAUGE -> trx("Latency gauge")
    HomePingSurface.CHIP -> trx("Shortcut pill")
    HomePingSurface.HEADER -> trx("Header pulse")
}

@Composable
private fun homePingSurfaceDetail(surface: HomePingSurface): String = when (surface) {
    HomePingSurface.GAUGE -> "The gauge and the latency cell on the connection page"
    HomePingSurface.CHIP -> "The ping pill next to add, paste and QR"
    HomePingSurface.HEADER -> "The round pulse control beside the wordmark"
}

@Composable
private fun HomePingControlsSettings(repo: AppRepository) {
    val settings = repo.settings
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomePingSurface.entries.forEach { surface ->
            val current = when (surface) {
                HomePingSurface.GAUGE -> parseHomePingAction(settings.homePingGaugeAction)
                HomePingSurface.CHIP -> parseHomePingAction(settings.homePingChipAction)
                HomePingSurface.HEADER -> parseHomePingAction(settings.homePingHeaderAction)
            }
            val shape = RoundedCornerShape(13.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(homeCloudInsetFill())
                    .border(1.dp, homeCloudInsetBorder(), shape)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeVectorIcon(HomeIcon.PING, Aether.Cyan, Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            homePingSurfaceTitle(surface),
                            color = Aether.Ink,
                            style = settingsRowTitleStyle(),
                            maxLines = 1
                        )
                        Text(
                            MarbleCopy.oneSentence(trx(homePingSurfaceDetail(surface))),
                            color = Aether.InkMuted,
                            style = settingsBodyStyle(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HomePingAction.entries.forEach { action ->
                        val selected = current == action
                        val chipShape = RoundedCornerShape(10.dp)
                        Text(
                            text = homePingActionLabel(action),
                            color = if (selected) Aether.Cyan else Aether.InkMuted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            modifier = Modifier
                                .clip(chipShape)
                                .background(
                                    if (selected) {
                                        Aether.Cyan.copy(alpha = .12f)
                                    } else {
                                        Aether.Glass.copy(alpha = .35f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (selected) Aether.Cyan.copy(alpha = .38f) else Color.Transparent,
                                    chipShape
                                )
                                .kineticClickable(role = Role.RadioButton, boundedShape = chipShape) {
                                    val next = repo.settings
                                    val updated = when (surface) {
                                        HomePingSurface.GAUGE ->
                                            next.copy(homePingGaugeAction = action.id)
                                        HomePingSurface.CHIP ->
                                            next.copy(homePingChipAction = action.id)
                                        HomePingSurface.HEADER ->
                                            next.copy(homePingHeaderAction = action.id)
                                    }
                                    repo.updateSettings(updated)
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
                Text(
                    MarbleCopy.oneSentence(homePingActionDetail(current)),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ServerLayoutPreview(layout: ServerLayout, tone: Color, active: Boolean) {
    val ink = if (active) tone else Aether.InkFaint
    val barShape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Aether.Glass.copy(alpha = .35f)),
        contentAlignment = Alignment.Center
    ) {
        if (layout == ServerLayout.ROW) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .width(if (index == 1) 20.dp else 24.dp)
                            .height(3.dp)
                            .clip(barShape)
                            .background(ink.copy(alpha = if (index == 0) .9f else .45f))
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(2) {
                            Box(
                                Modifier
                                    .size(11.dp)
                                    .clip(barShape)
                                    .background(ink.copy(alpha = .55f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsSettings(repo: AppRepository) {
    val underlay = repo.networkSnapshot

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
                    if (!repo.settings.ipv6Enabled) "IPv4-only TUN"
                    else if (familyPlan.blockIpv6Traffic) "::/0 blocked"
                    else "::/0 through the tunnel",
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
            when (repo.settings.addressFamilyMode) {
                AddressFamilyMode.SMART -> "SMART FAMILY"
                AddressFamilyMode.PREFER_IPV4 -> "IPv4 FIRST"
                AddressFamilyMode.PREFER_IPV6 -> "IPv6 FIRST"
                AddressFamilyMode.FORCE_IPV4 -> "IPv4 FORCED"
                AddressFamilyMode.FORCE_IPV6 -> "IPv6 FORCED"
            },
            when (repo.settings.addressFamilyMode) {
                AddressFamilyMode.FORCE_IPV4, AddressFamilyMode.FORCE_IPV6 -> Aether.Amber
                AddressFamilyMode.PREFER_IPV6 -> Aether.Cyan
                else -> Aether.Amethyst
            },
            compact = true
        )
        HoloBadge(underlay.label, Aether.InkMuted, compact = true)
    }

    Text(trx("IP version"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Text(
        trx("Changing the IPv6 mode reconnects, and Full TUN stays blocked during the switch."),
        color = Aether.InkMuted,
        style = MaterialTheme.typography.bodySmall
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf(
            AddressFamilyMode.SMART to "SMART",
            AddressFamilyMode.PREFER_IPV4 to "IPv4 FIRST",
            AddressFamilyMode.PREFER_IPV6 to "IPv6 FIRST",
            AddressFamilyMode.FORCE_IPV4 to "FORCE IPv4",
            AddressFamilyMode.FORCE_IPV6 to "FORCE IPv6"
        ).forEach { (mode, label) ->
            CyberChoiceChip(
                text = label,
                selected = repo.settings.addressFamilyMode == mode,
                color = when (mode) {
                    AddressFamilyMode.FORCE_IPV4, AddressFamilyMode.FORCE_IPV6 -> Aether.Amber
                    AddressFamilyMode.PREFER_IPV6 -> Aether.Cyan
                    else -> Aether.Amethyst
                }
            ) {
                repo.updateSettings(
                    repo.settings.copy(
                        addressFamilyMode = mode,
                        ipv6Enabled = mode != AddressFamilyMode.FORCE_IPV4,
                        preferIpv6 = mode in setOf(
                            AddressFamilyMode.SMART,
                            AddressFamilyMode.PREFER_IPV6,
                            AddressFamilyMode.FORCE_IPV6
                        ),
                        dnsQueryStrategy = when (mode) {
                            AddressFamilyMode.FORCE_IPV4 -> "UseIPv4"
                            AddressFamilyMode.FORCE_IPV6 -> "UseIPv6"
                            else -> "UseIP"
                        }
                    )
                )
            }
        }
    }

    if (repo.settings.addressFamilyMode == AddressFamilyMode.FORCE_IPV6 ||
        repo.settings.addressFamilyMode == AddressFamilyMode.FORCE_IPV4 ||
        repo.settings.strictAddressFamily
    ) {
        SettingSwitch(
            title = "Strict family enforcement",
            subtitle = if (repo.settings.strictAddressFamily) {
                "A server that cannot use the forced family is refused with the reason"
            } else {
                "A server that cannot use the forced family is dialled over the other one; " +
                    "IPv6 stays on for destinations"
            },
            checked = repo.settings.strictAddressFamily
        ) { repo.updateSettings(repo.settings.copy(strictAddressFamily = it)) }
        if (!repo.settings.strictAddressFamily) {
            Text(
                trx(
                    "Marble scans each server for IPv4 and IPv6 (Servers → ⋯ → Scan IPv4 / IPv6) " +
                        "and falls back automatically, so a forced family never blocks a " +
                        "connection on its own."
                ),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (repo.settings.connectionMode == ConnectionMode.FULL_TUN && repo.settings.dnsHijackEnabled) {
        Text(
            trx("Full TUN always intercepts IPv4 and IPv6 DNS on port 53; there is no plaintext fallback."),
            color = Aether.InkMuted,
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        SettingSwitch(
            title = "Intercept traditional DNS",
            subtitle = if (repo.settings.connectionMode == ConnectionMode.FULL_TUN)
                "Required to start Full TUN without plaintext DNS"
                else "Only traffic sent through the local proxy is protected",
            checked = repo.settings.dnsHijackEnabled
        ) { repo.updateSettings(repo.settings.copy(dnsHijackEnabled = it)) }
    }

    SettingSwitch(
        title = "Fake IP DNS",
        subtitle = "Instant local answers; real IP resolved in-tunnel",
        checked = repo.settings.dnsFakeIpEnabled
    ) { repo.updateSettings(repo.settings.copy(dnsFakeIpEnabled = it)) }
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
            Text(trx(title), color = Aether.Ink, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                trx(
                    when {
                        !ready -> "Missing"
                        stale -> "Stale"
                        else -> "Ready"
                    }
                ),
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            trx(
                if (!ready) "Tap Update to download"
                else "${formatBytes(bytes)} • ${relativeTime(updatedAt)}"
            ),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoutingEntryCard(
    repo: AppRepository,
    onOpen: () -> Unit
) {
    val s = repo.settings
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(homeCloudInsetFill())
            .border(1.dp, homeCloudInsetBorder(), shape)
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Aether.Emerald.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) {
            HomeVectorIcon(HomeIcon.ROUTING, Aether.Emerald, Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                trx("Open routing workspace"),
                color = Aether.Ink,
                style = settingsRowTitleStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (s.customRoutingEnabled) {
                    trx("Custom routing is ON")
                } else {
                    trx("Custom routing is off • tap to enable")
                },
                color = if (s.customRoutingEnabled) Aether.Emerald else Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HomeVectorIcon(HomeIcon.MORE, Aether.Emerald, Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsRoutingPage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    SettingsSubPage(
        title = trx("Routing"),
        subtitle = trx("Geo assets, rules, presets and the rule simulator"),
        onBack = onBack,
        listState = listState
    ) {
        SettingsHubCard(
            title = trx("Routing"),
            subtitle = trx("Configure exactly which traffic leaves through the tunnel"),
            tone = Aether.Emerald
        ) {
            RoutingSettings(repo)
        }
    }
}

@Composable
private fun GeoSourceSetControls(repo: AppRepository) {
    val settings = repo.settings
    val multi = settings.geoMultiSourceEnabled
    val ids = GeoAssetRegistry.parseIds(settings.geoAssetSourceIds)
    val signature = "${settings.geoAssetSourceIds}|${settings.geoCustomSourcesJson}|" +
        "${settings.geoIpUrl}|${settings.geoSiteUrl}|${settings.geoMultiSourceEnabled}"
    val catalog = remember(signature) { GeoAssetRegistry.catalog(settings) }
    val states = remember(signature, repo.geoGateNote) { repo.geoSourceStates() }

    SettingSwitch(
        title = "Read several geo databases",
        subtitle = "Use the geo tags of every source below at once",
        checked = multi
    ) { repo.updateSettings(repo.settings.copy(geoMultiSourceEnabled = it)) }

    Text(
        trx(repo.geoSourceSummary()),
        color = Aether.InkMuted,
        style = MaterialTheme.typography.labelSmall
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        catalog.forEach { spec ->
            val state = states.firstOrNull { it.spec.id == spec.id }
            val selected = spec.id in ids
            val shape = RoundedCornerShape(12.dp)
            val status = when {
                !selected -> trx("Not in use")
                state == null -> trx("Not downloaded yet")
                state.usable() -> GeoAssetRegistry.formatBytes(state.geoIpBytes + state.geoSiteBytes)
                else -> trx("Waiting for a download")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (selected) Aether.Emerald.copy(alpha = .09f) else homeCloudInsetFill())
                    .border(
                        1.dp,
                        if (selected) Aether.Emerald.copy(alpha = .34f) else homeCloudInsetBorder(),
                        shape
                    )
                    .kineticClickable(role = Role.Checkbox, boundedShape = shape) {
                        if (multi && selected && spec.id != ids.first()) {
                            repo.toggleGeoAssetSource(spec.id)
                        } else {
                            repo.applyGeoAssetSource(spec.id)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (selected) Aether.Emerald else Aether.InkFaint.copy(alpha = .30f))
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        trx(spec.label),
                        color = if (selected) Aether.Ink else Aether.InkMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        // MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one sentence per row.
                        MarbleCopy.oneSentence(
                            if (selected && spec.id == ids.first()) {
                                "${trx("Primary")} • $status"
                            } else {
                                status
                            }
                        ),
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

@Composable
private fun GeoPrecisionChoice(repo: AppRepository) {
    val precision = parseGeoPrecision(repo.settings.geoPrecision)
    Text(
        trx("Domestic separation"),
        color = Aether.InkFaint,
        style = MaterialTheme.typography.labelSmall
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        GeoPrecision.entries.forEach { level ->
            CyberChoiceChip(
                text = geoPrecisionLabel(level),
                selected = precision == level,
                color = Aether.Emerald
            ) { repo.setGeoPrecision(level) }
        }
    }
    Text(
        trx(geoPrecisionDetail(precision)) + " • " + repo.geoPrecisionSummary(),
        color = Aether.InkMuted,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
private fun geoPrecisionLabel(level: GeoPrecision): String = when (level) {
    GeoPrecision.STANDARD -> trx("Geo tags")
    GeoPrecision.ENHANCED -> trx("Curated")
    GeoPrecision.MAXIMUM -> trx("Strict")
}

@Composable
private fun geoPrecisionDetail(level: GeoPrecision): String = when (level) {
    GeoPrecision.STANDARD -> "Only the geo tags you configured"
    GeoPrecision.ENHANCED -> "Plus the domestic services MarbleNG keeps its own list of"
    GeoPrecision.MAXIMUM -> "Plus brand keywords, for services on a domain nobody published"
}

@Composable
private fun RoutingSettings(repo: AppRepository) {
    val s = repo.settings
    val assets = repo.routingAssetStatus()
    val rules = remember(s.routingRulesJson) { RoutingEngine.effectiveRules(s) }
    val implicit = remember(s.routingMode, s.routeBlockAds, s.routeAdsTag, s.routeGeoIpTags, s.routeGeoSiteTags, s.routeBypassPrivate) {
        RoutingEngine.implicitRules(s)
    }
    var sourceMenu by remember { mutableStateOf(false) }
    val currentSource = RoutingDefaults.sourceById(s.geoAssetSourceId)

    var editorRule by remember { mutableStateOf<RoutingRule?>(null) }
    var editorIsNew by remember { mutableStateOf(false) }

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

    Text(trx("Routing mode"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        RoutingModeRow(
            title = "Proxy all",
            detail = "Everything rides the tunnel",
            selected = s.routingMode == RoutingMode.PROXY_ALL,
            tone = Aether.Cyan,
            enabled = !repo.busy
        ) { repo.updateSettings(s.copy(routingMode = RoutingMode.PROXY_ALL)) }
        RoutingModeRow(
            title = "Private direct",
            detail = "LAN never enters the tunnel",
            selected = s.routingMode == RoutingMode.BYPASS_PRIVATE,
            tone = Aether.Emerald,
            enabled = !repo.busy
        ) { repo.updateSettings(s.copy(routingMode = RoutingMode.BYPASS_PRIVATE)) }
        RoutingModeRow(
            title = "Geo direct",
            detail = "Selected countries bypass the tunnel",
            selected = s.routingMode == RoutingMode.GEO_DIRECT,
            tone = Aether.Emerald,
            enabled = !repo.busy
        ) { repo.updateSettings(s.copy(routingMode = RoutingMode.GEO_DIRECT)) }
        RoutingModeRow(
            title = "Custom",
            detail = "Only your rules decide",
            selected = s.routingMode == RoutingMode.CUSTOM,
            tone = Aether.Amethyst,
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
                "IPIfNonMatch" to "Domain first (default)",
                "IPOnDemand" to "IP on demand",
                "AsIs" to "As-is (fastest)"
            ).forEach { (value, label) ->
                CyberChoiceChip(
                    text = label,
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

    Text(trx("Geo databases"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    GeoSourceSetControls(repo)
    GeoPrecisionChoice(repo)

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
                    text = { Text(trx(source.label)) },
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

    val geoGate = repo.geoGateNote.ifBlank {
        RoutingEngine.geoDowngradeReason(s, assets.geoIpReady, assets.geoSiteReady)
    }
    if (geoGate.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Aether.Amber.copy(alpha = .10f))
                .border(1.dp, Aether.Amber.copy(alpha = .28f), RoundedCornerShape(14.dp))
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            HomeVectorIcon(HomeIcon.INFO, Aether.Amber, Modifier.size(16.dp))
            Text(
                trx("Geo routing paused until the routing databases finish downloading"),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyberButton("Update", Aether.Cyan, Modifier.weight(1f), !repo.busy) {
            repo.prepareRoutingAssets(true)
        }
        CyberButton("Verify", Aether.Emerald, Modifier.weight(1f), repo.libraryProfiles.isNotEmpty() && !repo.busy) {
            repo.verifyRoutingPolicy()
        }
    }

    SettingSwitch(
        title = "Block ads",
        subtitle = "geosite ad categories never load — in every mode",
        checked = s.routeBlockAds
    ) { repo.updateSettings(s.copy(routeBlockAds = it)) }

    SettingSwitch(
        title = "Bypass private LAN",
        subtitle = if (implicit.forceBypassPrivate) "Always on in this mode" else "RFC1918 stays off the tunnel",
        checked = s.routeBypassPrivate || implicit.forceBypassPrivate
    ) { pinnedOn -> repo.updateSettings(s.copy(routeBypassPrivate = pinnedOn || implicit.forceBypassPrivate)) }

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

    RoutingSimulatorCard(repo)

    RoutingExpertSection(repo, s)

    confirmPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { confirmPreset = null },
            title = { Text(trx("Apply ${preset.title} preset?"), color = Aether.Ink) },
            text = {
                Text(
                    trx("Replaces your current rules with the preset list, leaving your mode, geo source and expert lists untouched."),
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
private fun RoutingModeRow(
    title: String,
    detail: String,
    selected: Boolean,
    tone: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
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
            .kineticClickable(
                enabled = enabled,
                role = Role.Button,
                boundedShape = shape,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                trx(title),
                color = if (selected) tone else Aether.Ink,
                style = settingsRowTitleStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                trx(detail),
                color = Aether.InkFaint,
                style = settingsBodyStyle(),
                maxLines = 1,
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
                HomeVectorIcon(HomeIcon.SORT, if (dragging) tone else Aether.InkFaint, Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))

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
                    HomeVectorIcon(HomeIcon.MORE, Aether.InkMuted, Modifier.size(18.dp))
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
                    HomeVectorIcon(HomeIcon.CANCEL, Aether.Cyan, Modifier.size(19.dp))
                }
            }

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

            Text(trx("Then what?"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(
                    RoutingOutbound.PROXY to Aether.Cyan,
                    RoutingOutbound.DIRECT to Aether.Emerald,
                    RoutingOutbound.BLOCK to Aether.Danger
                ).forEach { (outbound, tone) ->
                    CyberChoiceChip(
                        text = outbound.name,
                        selected = draft.outbound == outbound,
                        color = tone,
                        modifier = Modifier.weight(1f)
                    ) { draft = draft.copy(outbound = outbound) }
                }
            }

            issues.forEach { issue ->
                Text(
                    issue.message,
                    color = if (issue.severity == RoutingEngine.IssueSeverity.ERROR) Aether.Danger else Aether.Amber,
                    style = MaterialTheme.typography.labelSmall
                )
            }

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

    AnimatedVisibility(
        visible = expanded,
        enter = expressiveExpandVertically(),
        exit = expressiveCollapseVertically()
    ) {
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
                ).forEach { (value, label) ->
                    CyberChoiceChip(
                        text = label,
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
            Text(trx(current.headline),
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
                            Text(trx(check.title),color=Aether.Ink,style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                            HoloBadge(check.severity.name,c,true)
                        }
                        Text(trx(check.detail),color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)
                        if(check.action.isNotBlank()) Text("→ ${trx(check.action)}",color=c,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(current.failures>0) CyberButton("Safe runtime reset",Aether.Danger,Modifier.fillMaxWidth(),!repo.busy) { repo.safeRuntimeResetFromBugFinder() }
        }
    }
}

private fun probeMethodTitle(method: ProbeMethod): String = when (method) {
    ProbeMethod.REAL_DELAY -> "Real delay"
    ProbeMethod.TCP_PING -> "TCP ping"
    ProbeMethod.URL_TEST -> "URL test"
}

private fun probeMethodDetail(method: ProbeMethod): String = when (method) {
    ProbeMethod.REAL_DELAY ->
        "Opens a real HTTPS page through the tunnel and times it: the number a browser would feel"
    ProbeMethod.TCP_PING ->
        "TCP handshake to the server port: fastest liveness check, measured without the tunnel"
    ProbeMethod.URL_TEST ->
        "HTTPS HEAD through the selected core: native Clash delay on sing-box, verified SOCKS tunnel request on Xray"
}

private fun probeMethodShortLabel(method: ProbeMethod): String = when (method) {
    ProbeMethod.REAL_DELAY -> "Real delay"
    ProbeMethod.TCP_PING -> "TCP ping"
    ProbeMethod.URL_TEST -> "URL test"
}

@Composable
private fun ProbeSettings(repo: AppRepository) {
    val s = repo.settings
    val method = s.probeMethod

    Text(
        trx("Used by the Home ping button, subscription ping and Ping all."),
        color = Aether.InkMuted,
        style = settingsBodyStyle()
    )

    Text(
        trx("URL test needs the extended core; TCP ping checks only the endpoint, not the account or tunnel."),
        color = Aether.InkFaint,
        style = settingsBodyStyle()
    )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ProbeMethod.entries.forEach { candidate ->
            val selected = method == candidate

            val available = candidate.availableOn(s.coreEngine())
            val unavailable = candidate.unavailableReason(s.coreEngine())
            val tone = when (candidate) {
                ProbeMethod.REAL_DELAY -> Aether.Emerald
                ProbeMethod.TCP_PING -> Aether.CyanBright
                ProbeMethod.URL_TEST -> Aether.Amethyst
            }
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(marbleNativeContainerHigh())
                    .border(
                        1.dp,
                        if (selected) tone.copy(alpha = .58f) else marbleNativeOutline(),
                        shape
                    )
                    .alpha(if (available) 1f else .45f)
                    .kineticClickable(
                        enabled = available,
                        role = Role.Button,
                        boundedShape = shape
                    ) {
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
                        if (candidate == ProbeMethod.REAL_DELAY) {
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
                    if (unavailable.isNotBlank()) {
                        Text(
                            trx(unavailable),
                            color = Aether.Amber,
                            style = settingsBodyStyle(),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

    HorizontalDivider(color = Aether.GlassBorderSoft)

    Text(
        trx("Ping speed"),
        color = Aether.Ink,
        style = settingsRowTitleStyle()
    )
    Text(
        trx("The default pace is about 50% faster than the classic baseline for every method."),
        color = Aether.InkMuted,
        style = settingsBodyStyle()
    )

    SettingSwitch(
        title = "Custom speed",
        subtitle = if (s.pingSpeedCustom) {
            trx("Manual") + " • ${PingSpeed.percent(s.pingSpeedPercent)}% " + trx("of the classic pace")
        } else {
            trx("Off") + " • " + trx("default speed (~50% faster)")
        },
        checked = s.pingSpeedCustom
    ) { enabled ->
        repo.updateSettings(repo.settings.copy(pingSpeedCustom = enabled))
    }

    AnimatedVisibility(s.pingSpeedCustom) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            var dialPercent by remember(s.pingSpeedPercent) {
                mutableFloatStateOf(PingSpeed.percent(s.pingSpeedPercent).toFloat())
            }
            val committed = PingSpeed.stepped(dialPercent.toDouble())
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Slider(
                    value = dialPercent,
                    onValueChange = { dialPercent = it },
                    onValueChangeFinished = {
                        repo.updateSettings(
                            repo.settings.copy(pingSpeedPercent = committed)
                        )
                    },
                    valueRange = PingSpeed.MIN_PERCENT.toFloat()..PingSpeed.MAX_PERCENT.toFloat(),
                    steps = (PingSpeed.MAX_PERCENT - PingSpeed.MIN_PERCENT) / PingSpeed.STEP_PERCENT - 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$committed%",
                    color = Aether.Cyan,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    trx("Slower • ${PingSpeed.MIN_PERCENT}%"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    trx("Classic • 100%"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    trx("Faster • ${PingSpeed.MAX_PERCENT}%"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                trx("Now") + ": " + trx(PingSpeed.label(true, committed)) + " • " +
                    (s.pingConcurrency * PingSpeed.factor(true, committed)).roundToInt().coerceIn(1, 64) +
                    " " + trx("servers at once") + " • " +
                    (PingBudget.SAMPLE_SPACING_MS / PingSpeed.factor(true, committed)).roundToLong().coerceIn(20L, 300L) +
                    " ms " + trx("between samples"),
                color = Aether.InkFaint,
                style = settingsBodyStyle()
            )
            if (committed != PingSpeed.DEFAULT_PERCENT) {
                CyberButton(
                    label = trx("Use the default speed"),
                    color = Aether.Cyan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repo.updateSettings(
                        repo.settings.copy(pingSpeedCustom = false, pingSpeedPercent = PingSpeed.DEFAULT_PERCENT)
                    )
                }
            }
        }
    }

    if (!s.pingSpeedCustom) {
        Text(
            trx("Default speed") + " • " + trx("about 1.5× the classic pace") + " • " +
                s.pingWorkers() + " " + trx("servers at once") + " • " +
                s.pingSampleSpacingMs() + " ms " + trx("between samples"),
            color = Aether.InkFaint,
            style = settingsBodyStyle()
        )
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    SettingSwitch(
        title = "Also measure download speed",
        subtitle = "Slower, uses data",
        checked = s.probeSpeedTest
    ) { repo.updateSettings(repo.settings.copy(probeSpeedTest = it)) }

    AnimatedVisibility(
        s.probeSpeedTest,
        enter = expressiveExpandVertically(),
        exit = expressiveCollapseVertically()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SettingSwitch(
                title = "Grow the speed sample",
                subtitle = "More only from fast servers",
                checked = s.adaptiveThroughputEnabled
            ) { repo.updateSettings(repo.settings.copy(adaptiveThroughputEnabled = it)) }
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    Text(
        trx("Measurement budget"),
        color = Aether.Ink,
        style = settingsRowTitleStyle()
    )
    Text(
        trx("Timeout and sample count apply to every method; the width below is the sweep's concurrency."),
        color = Aether.InkMuted,
        style = settingsBodyStyle()
    )

    PingBudgetChoiceRow(
        title = "Timeout per server",
        detail = "How long one server may take before it counts as unreachable",
        selected = PingBudget.timeoutSec(s.pingTimeoutSec),
        choices = PingBudget.TIMEOUT_CHOICES,
        suffix = "s",
        tone = Aether.Cyan
    ) { repo.updateSettings(repo.settings.copy(pingTimeoutSec = PingBudget.timeoutSec(it))) }

    Text(
        trx("Parallel servers"),
        color = Aether.Ink,
        style = settingsRowTitleStyle()
    )
    Text(
        trx("More servers at once is faster but noisier, because parallel handshakes inflate each other."),
        color = Aether.InkMuted,
        style = settingsBodyStyle()
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PingParallelMode.entries.forEach { mode ->
            CyberChoiceChip(
                text = if (mode == PingParallelMode.AUTO) trx("Automatic") else trx("Manual"),
                selected = s.pingParallelMode == mode,
                color = Aether.Emerald,
                modifier = Modifier.weight(1f)
            ) {
                repo.updateSettings(repo.settings.copy(pingParallelMode = mode))
            }
        }
    }

    if (s.pingParallelMode == PingParallelMode.AUTO) {
        Text(
            trx("Automatic") + " • " + repo.pingParallelismSummary(),
            color = Aether.Emerald,
            style = settingsBodyStyle()
        )
        Text(
            trx("The number follows this device and is re-read on every sweep."),
            color = Aether.InkFaint,
            style = settingsBodyStyle()
        )
    } else {
        PingBudgetChoiceRow(
            title = "Servers at once",
            detail = "Fewer is slower but far more accurate on a weak link",
            selected = PingBudget.concurrency(s.pingConcurrency),
            choices = PingBudget.CONCURRENCY_CHOICES,
            suffix = "",
            tone = Aether.Emerald
        ) { repo.updateSettings(repo.settings.copy(pingConcurrency = PingBudget.concurrency(it))) }
        Text(
            trx("Automatic would pick") + " ${repo.recommendedPingParallelism()} " +
                trx("on this device") +
                " • " + repo.pingParallelismSummary(),
            color = Aether.InkFaint,
            style = settingsBodyStyle()
        )
    }

    PingBudgetChoiceRow(
        title = "Samples per server",
        detail = "The published latency is the median after the warm-up sample is discarded",
        selected = PingBudget.samples(s.pingSamples),
        choices = PingBudget.SAMPLE_CHOICES,
        suffix = "×",
        tone = Aether.Amethyst
    ) { repo.updateSettings(repo.settings.copy(pingSamples = PingBudget.samples(it))) }

    Text(
        "${trx("Worst case per server")}: " +
            "${PingBudget.perServerBudgetMs(s.pingTimeoutSec, s.pingSamples, s.pingSampleSpacingMs()) / 1000}s • " +
            "${repo.effectivePingParallelism()} ${trx("Direct at once")}",
        color = Aether.InkFaint,
        style = settingsBodyStyle()
    )
}

@Composable
private fun AutoServerSelectorSettings(repo: AppRepository) {
    val s = repo.settings
    val strategy = s.autoServerStrategyEnum
    val scope = s.autoServerScopeEnum
    val preview = repo.previewAutoServerChoice()

    SettingSwitch(
        title = "Automatic server selector",
        subtitle = if (s.autoServerSelectorEnabled) {
            "On • ${trx(AutoServerSelector.shortLabel(strategy))}"
        } else {
            "Off • manual selection still works"
        },
        checked = s.autoServerSelectorEnabled
    ) { enabled ->
        repo.updateSettings(repo.settings.copy(autoServerSelectorEnabled = enabled))
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)
    Text(trx("Choose a strategy"), color = Aether.Ink, style = settingsRowTitleStyle())
    val strategies = listOf(
        AutoServerStrategy.SMART,
        AutoServerStrategy.LEAST_PING,
        AutoServerStrategy.LEAST_LOAD,
        AutoServerStrategy.CUSTOM,
        AutoServerStrategy.ROUND_ROBIN,
        AutoServerStrategy.RANDOM
    )
    strategies.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            row.forEach { candidate ->
                CyberChoiceChip(
                    text = trx(AutoServerSelector.shortLabel(candidate)),
                    selected = strategy == candidate,
                    color = when (candidate) {
                        AutoServerStrategy.CUSTOM -> Aether.Amber
                        AutoServerStrategy.SMART -> Aether.Cyan
                        else -> Aether.Amethyst
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    repo.updateSettings(repo.settings.copy(autoServerStrategy = candidate.id))
                }
            }
        }
    }
    Text(
        trx(AutoServerSelector.describe(strategy)),
        color = Aether.InkFaint,
        style = settingsBodyStyle()
    )

    if (strategy == AutoServerStrategy.CUSTOM) {
        val weights = AutoServerWeights(
            ping = s.autoServerPingWeight,
            load = s.autoServerLoadWeight,
            stability = s.autoServerStabilityWeight,
            freshness = s.autoServerFreshnessWeight
        )
        val presets = listOf(
            "Balanced" to AutoServerWeights.BALANCED,
            "Ping first" to AutoServerWeights.PING_FIRST,
            "Load first" to AutoServerWeights.LOAD_FIRST,
            "Stable" to AutoServerWeights.STABILITY_FIRST
        )
        Text(trx("Custom priorities"), color = Aether.Ink, style = settingsRowTitleStyle())
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            presets.forEach { (label, preset) ->
                val selected = weights.clamped() == preset
                CyberChoiceChip(
                    text = trx(label),
                    selected = selected,
                    color = Aether.Amber
                ) {
                    repo.updateSettings(
                        repo.settings.copy(
                            autoServerStrategy = AutoServerStrategy.CUSTOM.id,
                            autoServerPingWeight = preset.ping,
                            autoServerLoadWeight = preset.load,
                            autoServerStabilityWeight = preset.stability,
                            autoServerFreshnessWeight = preset.freshness
                        )
                    )
                }
            }
        }
        Text(
            trx("Adjust the priorities; zero ignores a signal and weights normalize automatically."),
            color = Aether.InkFaint,
            style = settingsBodyStyle()
        )
        AutoServerWeightSlider("Ping", weights.ping, Aether.Cyan) { value ->
            repo.updateSettings(
                repo.settings.copy(autoServerPingWeight = value),
                coalesceWrite = true
            )
        }
        AutoServerWeightSlider("Load", weights.load, Aether.Emerald) { value ->
            repo.updateSettings(
                repo.settings.copy(autoServerLoadWeight = value),
                coalesceWrite = true
            )
        }
        AutoServerWeightSlider("Stability", weights.stability, Aether.Amethyst) { value ->
            repo.updateSettings(
                repo.settings.copy(autoServerStabilityWeight = value),
                coalesceWrite = true
            )
        }
        AutoServerWeightSlider("Freshness", weights.freshness, Aether.Amber) { value ->
            repo.updateSettings(
                repo.settings.copy(autoServerFreshnessWeight = value),
                coalesceWrite = true
            )
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)
    Text(trx("Search pool"), color = Aether.Ink, style = settingsRowTitleStyle())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        AutoServerScope.entries.forEach { candidate ->
            CyberChoiceChip(
                text = trx(if (candidate == AutoServerScope.SOURCE) "This source" else "Whole library"),
                selected = scope == candidate,
                color = Aether.Emerald,
                modifier = Modifier.weight(1f)
            ) {
                repo.updateSettings(repo.settings.copy(autoServerScope = candidate.id))
            }
        }
    }

    Text(trx("Automatic actions"), color = Aether.Ink, style = settingsRowTitleStyle())
    SettingSwitch(
        title = "After a ping sweep",
        subtitle = "Select the top-ranked server",
        checked = s.autoServerOnScan
    ) { repo.updateSettings(repo.settings.copy(autoServerOnScan = it)) }
    SettingSwitch(
        title = "When I press connect",
        subtitle = "Choose for me if no server is selected",
        checked = s.autoServerOnConnect
    ) { repo.updateSettings(repo.settings.copy(autoServerOnConnect = it)) }
    SettingSwitch(
        title = "When the route fails",
        subtitle = "Fail over to a measured server",
        checked = s.autoServerOnFailure
    ) { repo.updateSettings(repo.settings.copy(autoServerOnFailure = it)) }

    HorizontalDivider(color = Aether.GlassBorderSoft)
    Text(trx("Switch margin"), color = Aether.Ink, style = settingsRowTitleStyle())
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf(5, 10, 15, 25, 40).forEach { percent ->
            CyberChoiceChip(
                text = "$percent%",
                selected = s.autoServerSwitchMarginPercent == percent,
                color = Aether.Amber
            ) {
                repo.updateSettings(repo.settings.copy(autoServerSwitchMarginPercent = percent))
            }
        }
    }
    Text(
        trx("A challenger must score this much higher before a healthy route changes."),
        color = Aether.InkFaint,
        style = settingsBodyStyle()
    )

    HorizontalDivider(color = Aether.GlassBorderSoft)
    Text(trx("Live recommendation"), color = Aether.Ink, style = settingsRowTitleStyle())
    val previewShape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(previewShape)
            .background(homeCloudInsetFill())
            .border(1.dp, homeCloudInsetBorder(), previewShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Aether.Cyan.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                HomeVectorIcon(HomeIcon.SPARK, Aether.Cyan, Modifier.size(19.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    preview.profile?.let { stripLeadingFlag(it.name) } ?: trx("Measure servers first"),
                    color = Aether.Ink,
                    style = settingsRowTitleStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    trx(preview.reason),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            preview.ranking.firstOrNull()?.let { best ->
                Text(
                    "${(best.score * 100).roundToInt()}",
                    color = Aether.Cyan,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        preview.ranking.drop(1).take(2).forEachIndexed { index, score ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${index + 2}", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                Text(
                    stripLeadingFlag(score.profile.name),
                    modifier = Modifier.weight(1f),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${(score.score * 100).roundToInt()}",
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    CyberButton(
        label = trx(if (preview.profile == null) "Measure candidate servers" else "Select recommended server"),
        color = Aether.Cyan,
        modifier = Modifier.fillMaxWidth(),
        enabled = !repo.busy && !repo.probeActive
    ) {
        if (preview.profile == null) {
            repo.pingAutoServerPool()
        } else {
            repo.runAutoServerSelection(reason = "manual", connect = false)
        }
    }
}

@Composable
private fun AutoServerWeightSlider(
    label: String,
    value: Int,
    tone: Color,
    onValueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(trx(label), color = Aether.Ink, style = settingsBodyStyle())
            Spacer(Modifier.weight(1f))
            Text(
                value.coerceIn(0, 100).toString(),
                color = tone,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.coerceIn(0, 100).toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = tone,
                activeTrackColor = tone,
                inactiveTrackColor = tone.copy(alpha = .20f)
            )
        )
    }
}

@Composable
private fun PingBudgetChoiceRow(
    title: String,
    detail: String,
    selected: Int,
    choices: List<Int>,
    suffix: String,
    tone: Color,
    onPick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(trx(title), color = Aether.Ink, style = settingsRowTitleStyle())
        Text(trx(detail), color = Aether.InkFaint, style = settingsBodyStyle())
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            choices.forEach { value ->
                CyberChoiceChip(
                    text = "$value$suffix",
                    selected = value == selected,
                    color = tone,
                    onClick = { onPick(value) }
                )
            }
        }
    }
}

private fun dockSettingsSubtitle(settings: AppSettings): String {
    val size = when (parseDockSize(settings.dockSize)) {
        DockSize.SMALL -> "Small"
        DockSize.MEDIUM -> "Medium"
        DockSize.LARGE -> "Large"
    }
    val content = when {
        dockShowsIcons(settings.dockShowIcons, settings.dockShowLabels) &&
            dockShowsLabels(settings.dockShowIcons, settings.dockShowLabels) -> "icons + labels"
        dockShowsLabels(settings.dockShowIcons, settings.dockShowLabels) -> "labels only"
        else -> "icons only"
    }
    return "$size • $content"
}

@Composable
private fun DockSettings(repo: AppRepository) {
    val s = repo.settings
    val labels = dockShowsLabels(s.dockShowIcons, s.dockShowLabels)
    val icons = dockShowsIcons(s.dockShowIcons, s.dockShowLabels)

    Text(
        trx("The bottom bar of the app: what it shows and how much room it takes."),
        color = Aether.InkMuted,
        style = settingsBodyStyle()
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DockSize.entries.forEach { size ->
            CyberSegment(
                label = when (size) {
                    DockSize.SMALL -> "Small"
                    DockSize.MEDIUM -> "Medium"
                    DockSize.LARGE -> "Large"
                },
                detail = when (size) {
                    DockSize.SMALL -> "50 dp"
                    DockSize.MEDIUM -> "62 dp"
                    DockSize.LARGE -> "74 dp"
                },
                selected = parseDockSize(s.dockSize) == size,
                color = Aether.Emerald,
                modifier = Modifier.weight(1f)
            ) {
                repo.updateSettings(repo.settings.copy(dockSize = size.id))
            }
        }
    }

    SettingSwitch(
        title = "Show labels",
        subtitle = "Tab captions under the bar's glyphs",
        checked = labels
    ) { enabled ->
        repo.updateSettings(
            repo.settings.copy(
                dockShowLabels = enabled,

                dockShowIcons = if (!enabled) true else repo.settings.dockShowIcons
            )
        )
    }

    SettingSwitch(
        title = "Show icons",
        subtitle = "Tab glyphs; turning both off is not allowed",
        checked = icons
    ) { enabled ->
        repo.updateSettings(
            repo.settings.copy(
                dockShowIcons = enabled,
                dockShowLabels = if (!enabled) true else repo.settings.dockShowLabels
            )
        )
    }
}

private fun dockSlotKindLabel(kind: DockSlotKind): String = when (kind) {
    DockSlotKind.PULSE -> "Live pulse"
    DockSlotKind.SOURCE -> "One subscription"
    DockSlotKind.CONFIG -> "One config"
}

private fun dockSlotKindDetail(kind: DockSlotKind): String = when (kind) {
    DockSlotKind.PULSE -> "State, rates and tools"
    DockSlotKind.SOURCE -> "Every server of one source"
    DockSlotKind.CONFIG -> "One saved config, one tap"
}

@Composable
private fun dockSlotKindTone(kind: DockSlotKind): Color = when (kind) {
    DockSlotKind.PULSE -> Aether.Cyan
    DockSlotKind.SOURCE -> Aether.Emerald
    DockSlotKind.CONFIG -> Aether.Amethyst
}

private fun dockSlotSettingsSubtitle(settings: AppSettings, target: DockSlotTarget): String {
    if (!settings.dockSlotEnabled) return "Hidden • the bar keeps three tabs"
    val caption = dockSlotCaption(settings.dockSlotLabel)
    return listOf(dockSlotKindLabel(target.kind), caption.ifBlank { target.displayName })
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}

@Composable
private fun SettingsDockSlotPage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val settings = repo.settings
    val kind = parseDockSlotKind(settings.dockSlotKind)
    val target = dockSlotTarget(repo, kind)
    val icon = parseDockSlotIcon(settings.dockSlotIcon)
    val accent = dockSlotAccentTone(parseDockSlotAccent(settings.dockSlotAccent))
    val badgeTone = dockSlotStateTone(repo.state)
    val effectiveCaption = dockSlotCaption(settings.dockSlotLabel).ifBlank {
        dockSlotCaptionText(kind, target.displayName)
    }

    SettingsSubPage(
        title = "Fourth tab",
        subtitle = "Build a shortcut for the route, source or live signal you reach for most",
        onBack = onBack,
        listState = listState
    ) {
        SettingsHubCard(
            title = "Live preview",
            subtitle = "Every change below is applied to the bar immediately",
            tone = accent
        ) {
            DockSlotBarPreview(
                caption = effectiveCaption,
                icon = icon,
                enabled = settings.dockSlotEnabled,
                active = true,
                tone = accent,
                showStatusBadge = settings.dockSlotShowStatusBadge,
                badgeTone = badgeTone
            )
            Text(
                if (settings.dockSlotEnabled) {
                    "${dockSlotKindLabel(kind)}  •  ${target.displayName.ifBlank { dockSlotKindDetail(kind) }}"
                } else {
                    "The fourth tab is hidden — turn it back on to put it in the bar."
                },
                color = Aether.InkMuted,
                style = settingsBodyStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        SettingsHubCard(
            title = "Availability",
            subtitle = "Keep four destinations, or return to the original three-tab bar",
            tone = Aether.Cyan
        ) {
            SettingSwitch(
                title = "Show the fourth tab",
                subtitle = "Hide it without deleting your saved target, name or appearance.",
                checked = settings.dockSlotEnabled
            ) { repo.updateSettings(repo.settings.copy(dockSlotEnabled = it)) }
        }

        SettingsHubCard(
            title = "What this tab opens",
            subtitle = "Choose the job this slot does; then pick its target below",
            tone = accent
        ) {
            DockSlotKind.entries.forEach { candidate ->
                DockSlotKindChoice(
                    kind = candidate,
                    selected = kind == candidate,
                    tone = if (kind == candidate) accent else dockSlotKindTone(candidate)
                ) {
                    repo.updateSettings(repo.settings.copy(dockSlotKind = candidate.id))
                }
            }
        }

        SettingsHubCard(
            title = "Choose the target",
            subtitle = when (kind) {
                DockSlotKind.PULSE -> "Live route telemetry follows the connection automatically"
                DockSlotKind.SOURCE -> "Pin one subscription or the whole library"
                DockSlotKind.CONFIG -> "Pin one saved config for one-tap access"
            },
            tone = dockSlotKindTone(kind)
        ) {
            when (kind) {
                DockSlotKind.PULSE -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        HomeGlyphIcon(HomeGlyph.PULSE, accent, Modifier.size(19.dp))
                    }
                    Text(
                        trx("The pulse follows the route carrying traffic and keeps library tools one tap away."),
                        color = Aether.InkMuted,
                        style = settingsBodyStyle(),
                        modifier = Modifier.weight(1f)
                    )
                }
                DockSlotKind.SOURCE -> DockSlotSourcePicker(repo)
                DockSlotKind.CONFIG -> DockSlotConfigPicker(repo, target)
            }
        }

        SettingsHubCard(
            title = "Name and symbol",
            subtitle = "Make the slot recognizable in the narrow bottom bar",
            tone = accent
        ) {
            DockSlotCaptionField(repo)
            Text(
                trx("Tab icon"),
                color = Aether.Ink,
                style = settingsRowTitleStyle()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DockSlotIcon.entries.forEach { candidate ->
                    DockSlotIconChoice(
                        icon = candidate,
                        selected = icon == candidate,
                        tone = accent,
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.updateSettings(repo.settings.copy(dockSlotIcon = candidate.id))
                    }
                }
            }
        }

        SettingsHubCard(
            title = "Color and status",
            subtitle = "Give the fourth destination its own accent and live indicator",
            tone = accent
        ) {
            Text(
                trx("Accent color"),
                color = Aether.Ink,
                style = settingsRowTitleStyle()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DockSlotAccent.entries.forEach { candidate ->
                    DockSlotAccentChoice(
                        accent = candidate,
                        selected = parseDockSlotAccent(settings.dockSlotAccent) == candidate,
                        modifier = Modifier.weight(1f)
                    ) {
                        repo.updateSettings(repo.settings.copy(dockSlotAccent = candidate.id))
                    }
                }
            }
            SettingSwitch(
                title = "Live connection dot",
                subtitle = "Show connected, connecting or stopped state on the fourth tab icon.",
                checked = settings.dockSlotShowStatusBadge
            ) { repo.updateSettings(repo.settings.copy(dockSlotShowStatusBadge = it)) }
        }
    }
}

@Composable
private fun DockSlotKindChoice(
    kind: DockSlotKind,
    selected: Boolean,
    tone: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(15.dp)

    val fill by animateColorAsState(
        targetValue = if (selected) {
            tone.copy(alpha = .12f).compositeOver(homeCloudInsetFill())
        } else {
            homeCloudInsetFill()
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "dock-kind-fill-${kind.id}"
    )
    val border by animateColorAsState(
        targetValue = if (selected) tone.copy(alpha = .48f) else homeCloudInsetBorder(),
        animationSpec = MarbleMotionSpecs.Color,
        label = "dock-kind-border-${kind.id}"
    )
    val glyph = when (kind) {
        DockSlotKind.PULSE -> HomeGlyph.PULSE
        DockSlotKind.SOURCE -> HomeGlyph.LIBRARY
        DockSlotKind.CONFIG -> HomeGlyph.BOLT
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .kineticClickable(role = Role.RadioButton, boundedShape = shape, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = .13f)),
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(glyph, tone, Modifier.size(19.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                trx(dockSlotKindLabel(kind)),
                color = if (selected) tone else Aether.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                trx(dockSlotKindDetail(kind)),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tone)
                    .marblePopWhen(selected, peak = 1.12f),
                contentAlignment = Alignment.Center
            ) {
                HomeGlyphIcon(HomeGlyph.CHECK, Color.White, Modifier.size(11.dp))
            }
        } else {
            Box(Modifier.size(20.dp).border(1.dp, Aether.InkFaint.copy(alpha = .4f), CircleShape))
        }
    }
}

@Composable
private fun DockSlotAccentChoice(
    accent: DockSlotAccent,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tone = dockSlotAccentTone(accent)
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(if (selected) tone.copy(alpha = .13f) else homeCloudInsetFill())
            .border(
                1.dp,
                if (selected) tone.copy(alpha = .5f) else homeCloudInsetBorder(),
                shape
            )
            .kineticClickable(role = Role.RadioButton, boundedShape = shape, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(tone)
                .then(if (selected) Modifier.border(2.dp, Aether.Ink.copy(alpha = .22f), CircleShape) else Modifier)
                .marblePopWhen(selected, peak = 1.12f)
        )
        Text(
            trx(dockSlotAccentLabel(accent)),
            color = if (selected) tone else Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun dockSlotAccentLabel(accent: DockSlotAccent): String = when (accent) {
    DockSlotAccent.OCEAN -> "Ocean"
    DockSlotAccent.MINT -> "Mint"
    DockSlotAccent.VIOLET -> "Violet"
    DockSlotAccent.AMBER -> "Amber"
}

@Composable
private fun DockSlotBarPreview(
    caption: String,
    icon: DockSlotIcon,
    enabled: Boolean,
    active: Boolean,
    tone: Color,
    showStatusBadge: Boolean,
    badgeTone: Color
) {
    val dim = if (enabled) 1f else .38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Aether.VoidElevated)
            .border(1.dp, Aether.GlassBorderSoft.copy(alpha = .55f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            Tr.now.tabHome to HomeIcon.MODE,
            Tr.now.tabLibrary to HomeIcon.LIBRARY,
            Tr.now.tabSettings to HomeIcon.STATUS
        ).forEach { (label, glyph) ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeVectorIcon(glyph, Aether.InkFaint, Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    label,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .alpha(dim)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) tone.copy(alpha = .16f) else Color.Transparent)
                .border(
                    1.dp,
                    if (active) tone.copy(alpha = .34f) else Color.Transparent,
                    RoundedCornerShape(14.dp)
                )
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val glyphInk = tone
            Box(Modifier.size(18.dp)) {
                Canvas(Modifier.align(Alignment.Center).size(14.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = 1.6.dp.toPx()
                    val line = Stroke(width = stroke, cap = StrokeCap.Round)
                    when (icon) {
                    DockSlotIcon.PULSE -> drawPath(
                        Path().apply {
                            moveTo(w * .14f, h * .52f)
                            lineTo(w * .32f, h * .52f)
                            lineTo(w * .42f, h * .26f)
                            lineTo(w * .56f, h * .76f)
                            lineTo(w * .66f, h * .44f)
                            lineTo(w * .86f, h * .44f)
                        },
                        glyphInk,
                        style = line
                    )

                    DockSlotIcon.SPARK -> drawPath(
                        Path().apply {
                            moveTo(w * .58f, h * .12f)
                            lineTo(w * .28f, h * .54f)
                            lineTo(w * .48f, h * .54f)
                            lineTo(w * .40f, h * .88f)
                            lineTo(w * .72f, h * .44f)
                            lineTo(w * .52f, h * .44f)
                            close()
                        },
                        glyphInk,
                        style = line
                    )

                    DockSlotIcon.LAYERS -> listOf(.30f, .50f, .70f).forEachIndexed { index, y ->
                        val inset = if (index == 1) .16f else .22f
                        drawLine(
                            glyphInk,
                            Offset(w * inset, h * y),
                            Offset(w * (1f - inset), h * y),
                            stroke,
                            StrokeCap.Round
                        )
                    }

                    DockSlotIcon.BEARING -> {
                        drawCircle(
                            glyphInk,
                            radius = w * .32f,
                            center = Offset(w * .5f, h * .5f),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawLine(glyphInk, Offset(w * .5f, h * .5f), Offset(w * .70f, h * .30f), stroke, StrokeCap.Round)
                        drawLine(glyphInk, Offset(w * .5f, h * .5f), Offset(w * .38f, h * .62f), stroke, StrokeCap.Round)
                    }
                    }
                }
                if (showStatusBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(badgeTone)
                            .border(1.5.dp, Aether.VoidElevated, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(5.dp))
            Text(
                caption,
                color = if (active) tone else Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    if (!enabled) {
        Text(
            trx("The fourth slot is off, so the bar draws three tabs."),
            color = Aether.InkFaint,
            style = settingsBodyStyle()
        )
    }
}

@Composable
private fun DockSlotIconChoice(
    icon: DockSlotIcon,
    selected: Boolean,
    tone: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    val glyphInk = if (selected) tone else Aether.InkMuted
    Column(
        modifier = modifier
            .heightIn(min = 62.dp)
            .clip(shape)
            .background(if (selected) tone.copy(alpha = .14f) else homeCloudInsetFill())
            .border(
                1.dp,
                if (selected) tone.copy(alpha = .48f) else homeCloudInsetBorder(),
                shape
            )
            .kineticClickable(role = Role.RadioButton, boundedShape = shape, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.9.dp.toPx()
            val line = Stroke(width = stroke, cap = StrokeCap.Round)
            val ink = glyphInk
            when (icon) {
                DockSlotIcon.PULSE -> drawPath(
                    Path().apply {
                        moveTo(w * .14f, h * .52f)
                        lineTo(w * .32f, h * .52f)
                        lineTo(w * .42f, h * .26f)
                        lineTo(w * .56f, h * .76f)
                        lineTo(w * .66f, h * .44f)
                        lineTo(w * .86f, h * .44f)
                    },
                    ink,
                    style = line
                )

                DockSlotIcon.SPARK -> drawPath(
                    Path().apply {
                        moveTo(w * .58f, h * .12f)
                        lineTo(w * .28f, h * .54f)
                        lineTo(w * .48f, h * .54f)
                        lineTo(w * .40f, h * .88f)
                        lineTo(w * .72f, h * .44f)
                        lineTo(w * .52f, h * .44f)
                        close()
                    },
                    ink,
                    style = line
                )

                DockSlotIcon.LAYERS -> listOf(.30f, .50f, .70f).forEachIndexed { index, y ->
                    val inset = if (index == 1) .16f else .22f
                    drawLine(
                        ink,
                        Offset(w * inset, h * y),
                        Offset(w * (1f - inset), h * y),
                        stroke,
                        StrokeCap.Round
                    )
                }

                DockSlotIcon.BEARING -> {
                    drawCircle(
                        ink,
                        radius = w * .32f,
                        center = Offset(w * .5f, h * .5f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawLine(ink, Offset(w * .5f, h * .5f), Offset(w * .70f, h * .30f), stroke, StrokeCap.Round)
                    drawLine(ink, Offset(w * .5f, h * .5f), Offset(w * .38f, h * .62f), stroke, StrokeCap.Round)
                }
            }
        }
        Text(
            trx(dockSlotIconLabel(icon)),
            color = if (selected) tone else Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun dockSlotIconLabel(icon: DockSlotIcon): String = when (icon) {
    DockSlotIcon.PULSE -> "Pulse"
    DockSlotIcon.SPARK -> "Spark"
    DockSlotIcon.LAYERS -> "Layers"
    DockSlotIcon.BEARING -> "Bearing"
}

@Composable
private fun DockSlotPickerRow(
    title: String,
    detail: String,
    trailing: String,
    selected: Boolean,
    tone: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) tone.copy(alpha = .12f) else homeCloudInsetFill())
            .border(
                1.dp,
                if (selected) tone.copy(alpha = .42f) else homeCloudInsetBorder(),
                shape
            )
            .kineticClickable(role = Role.RadioButton, boundedShape = shape, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                color = Aether.Ink,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                color = if (selected) tone else Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(tone),
                contentAlignment = Alignment.Center
            ) {
                HomeGlyphIcon(HomeGlyph.CHECK, Color.White, Modifier.size(10.dp))
            }
        } else {
            Spacer(Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DockSlotSourcePicker(repo: AppRepository) {
    val settings = repo.settings
    val pinned = settings.dockSlotSourceId.trim().ifBlank { "all" }
    val library = repo.libraryProfiles

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            trx("Which source"),
            color = Aether.Ink,
            style = settingsRowTitleStyle()
        )
        DockSlotPickerRow(
            title = trx(DOCK_SLOT_ALL_SOURCES),
            detail = trx("Every server in the library"),
            trailing = "${library.size}",
            selected = pinned == "all",
            tone = Aether.Emerald
        ) { repo.updateSettings(repo.settings.copy(dockSlotSourceId = "all")) }

        DockSlotPickerRow(
            title = trx("Manual"),
            detail = trx("Servers you added yourself"),
            trailing = "${library.count { it.subscriptionId == "manual" }}",
            selected = pinned == "manual",
            tone = Aether.Emerald
        ) { repo.updateSettings(repo.settings.copy(dockSlotSourceId = "manual")) }

        repo.subscriptions.forEach { sub ->
            DockSlotPickerRow(
                title = sub.name,
                detail = if (sub.url.isBlank()) trx("Local source") else trx("Subscription"),
                trailing = "${library.count { it.subscriptionId == sub.id }}",
                selected = pinned == sub.id,
                tone = Aether.Emerald
            ) { repo.updateSettings(repo.settings.copy(dockSlotSourceId = sub.id)) }
        }

        if (repo.subscriptions.isEmpty()) {
            Text(
                trx("No subscriptions yet — add one from the Servers page and it appears here."),
                color = Aether.InkFaint,
                style = settingsBodyStyle()
            )
        }
    }
}

@Composable
private fun DockSlotConfigPicker(repo: AppRepository, target: DockSlotTarget) {
    var search by remember { mutableStateOf("") }
    val benchmarks = repo.benchmarks.associateBy { it.profileId }
    val pinned = target.profiles.firstOrNull()
    val matches = ServersQuery.sort(
        profiles = ServersQuery.visible(
            profiles = repo.libraryProfiles,
            filter = ServersFilter(query = search),
            benchmarks = benchmarks
        ),
        mode = NodeSortMode.DEFAULT,
        reverse = false,
        benchmarks = benchmarks
    ).take(DOCK_SLOT_PICKER_CAP)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            trx("Which config"),
            color = Aether.Ink,
            style = settingsRowTitleStyle()
        )
        if (pinned != null) {
            Text(
                "${trx("Pinned")}: ${stripLeadingFlag(pinned.name)}",
                color = Aether.Amethyst,
                style = settingsBodyStyle()
            )
        }
        ServersSearchField(
            value = search,
            onValueChange = { search = it },
            onClear = { search = "" }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (matches.isEmpty()) {
                Text(
                    if (repo.libraryProfiles.isEmpty()) {
                        trx("The library is empty — add a config from the Servers page first.")
                    } else {
                        trx("No config matches this search.")
                    },
                    color = Aether.InkFaint,
                    style = settingsBodyStyle()
                )
            }
            matches.forEach { profile ->
                val isPinned = pinned?.id == profile.id && pinned.subscriptionId == profile.subscriptionId
                DockSlotPickerRow(
                    title = stripLeadingFlag(profile.name),
                    detail = listOf(ServersQuery.badge(profile), ServersQuery.address(profile))
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    trailing = ServersQuery.measuredMs(profile, benchmarks)
                        .takeIf { it > 0 }?.let { "$it ms" }
                        ?: "",
                    selected = isPinned,
                    tone = Aether.Amethyst
                ) {
                    repo.updateSettings(
                        repo.settings.copy(
                            dockSlotProfileId = profile.id,
                            dockSlotProfileSourceId = profile.subscriptionId
                        )
                    )
                }
            }
            if (matches.size >= DOCK_SLOT_PICKER_CAP) {
                Text(
                    trx("Showing the first $DOCK_SLOT_PICKER_CAP configs — search to narrow them down."),
                    color = Aether.InkFaint,
                    style = settingsBodyStyle()
                )
            }
        }
    }
}

@Composable
private fun DockSlotCaptionField(repo: AppRepository) {
    val settings = repo.settings
    var draft by remember { mutableStateOf(settings.dockSlotLabel) }
    val kind = parseDockSlotKind(settings.dockSlotKind)
    val target = dockSlotTarget(repo, kind)
    val fallback = dockSlotCaptionText(kind, target.displayName)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            trx("Tab name"),
            color = Aether.Ink,
            style = settingsRowTitleStyle()
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { raw ->

                val bounded = raw.take(DOCK_SLOT_CAPTION_MAX * 2)
                draft = bounded
                val stored = dockSlotCaption(bounded)
                if (stored != repo.settings.dockSlotLabel) {
                    repo.updateSettings(repo.settings.copy(dockSlotLabel = stored))
                }
            },
            label = { Text(trx("Shown under the icon")) },
            singleLine = true,
            shape = ServersCardShape,
            modifier = Modifier.fillMaxWidth(),
            colors = marbleOutlinedTextFieldColors()
        )
        Text(
            "${trx("Blank uses")} $fallback",
            color = Aether.InkFaint,
            style = settingsBodyStyle()
        )
        if (settings.dockSlotLabel.isNotBlank()) {
            CyberButton(
                label = "Use the target name",
                color = Aether.Cyan,
                icon = HomeIcon.RESET,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    draft = ""
                    repo.updateSettings(repo.settings.copy(dockSlotLabel = ""))
                }
            )
        }
    }
}

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
                        if(resolved == PrismButtonVariant.Primary ||
                            resolved == PrismButtonVariant.Danger
                        ) LocalContentColor.current else color
                    } else Aether.InkFaint,

                    Modifier.size(if(compact) 18.dp else 20.dp)
                )
            }
        }
    )
}

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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tone = if (selectionTone == Color.Unspecified) color else selectionTone

    val shape = marbleNativePill()
    val outline = marbleNativeOutline()

    val selectedFill = marbleNativeSelectedFill(tone)
    val fill by animateColorAsState(
        if (selected) selectedFill else tone.copy(alpha = 0f),
        MarbleMotionSpecs.Color,
        label = "choice-chip-fill"
    )
    val ink by animateColorAsState(
        if (selected) tone else marbleNativeInkVariant(),
        MarbleMotionSpecs.Color,
        label = "choice-chip-ink"
    )
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(fill)
            .border(
                1.dp,
                if (selected) tone.copy(alpha = .34f) else outline,
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
    val tone = if (selectionTone == Color.Unspecified) color else selectionTone

    val shape = marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)
    val outline = marbleNativeOutline()
    val restingFill = marbleNativeCardFill()
    val chosenFill = marbleNativeSelectedFill(tone)
    val fill by animateColorAsState(
        if (selected) chosenFill else restingFill,
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
                if (selected) tone.copy(alpha = .34f) else outline,
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
    val tone = settingsSectionTone()
    val localizedTitle = trx(title)
    val switchWord = trx("switch")
    val localizedState = trx(if (checked) "on" else "off")
    val accessibilityState = trx(if (checked) "Enabled" else "Disabled")
    var lastClickTime by remember { mutableLongStateOf(0L) }

    MarbleNativeListItem(
        title = title,
        subtitle = MarbleCopy.oneSentence(trx(subtitle)),
        tone = tone,
        selected = checked,
        role = Role.Switch,
        onClick = {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > debounceMs) {
                lastClickTime = now
                onChecked(!checked)
            }
        },
        modifier = Modifier.semantics {
            contentDescription = "$localizedTitle، $switchWord $localizedState"
            stateDescription = accessibilityState
        },
        trailing = {
            MarbleNativeSwitch(
                checked = checked,
                onCheckedChange = { next -> onChecked(next) }
            )
        }
    )
}

@Composable
private fun NumberSetting(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onValue: (Int) -> Unit
) {
    val tone = settingsSectionTone()
    val shape = marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)

    val shown = value.coerceIn(range)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MarbleNativeSpec.TouchTargetDp.dp)
            .clip(shape)
            .background(marbleNativeCardFill())
            .border(1.dp, marbleNativeOutline(), shape)
            .padding(
                horizontal = MarbleNativeSpec.RowPaddingHorizontalDp.dp - 4.dp,
                vertical = 5.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            trx(title),
            color = Aether.Ink,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
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
            color = tone,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.Center
        )

        PrismIconButton(
            onClick = { onValue((shown + 1).coerceAtMost(range.last)) },
            tone = tone,
            selected = true,
            size = 30.dp,
            descriptiveLabel = "Increase $title"
        ) {
            Text("+", color = tone, style = MaterialTheme.typography.bodyMedium)
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
    val ime = rememberMarbleImeHider()
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(trx(label)) },
        singleLine = true,
        modifier = modifier,

        keyboardOptions = marbleImeOptions(ImeAction.Done),
        keyboardActions = marbleImeActions(ime),
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

@Composable
private fun healthColor(latencyMs: Int, success: Int): Color = when {
    success <= 0 -> Aether.InkFaint
    success < 50 -> Aether.Danger
    latencyMs in 1..99 && success >= 80 -> Aether.Emerald
    latencyMs in 100..250 -> Aether.Amber
    latencyMs > 250 -> Aether.Danger
    else -> Aether.Cyan
}

private fun libraryPingQuality(latencyMs: Int): String = when {
    latencyMs <= 0 -> "Waiting"
    latencyMs < 100 -> "Fast"
    latencyMs <= 250 -> "Fair"
    else -> "Slow"
}

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
        "DNS order, MTU, failover, routing",
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
