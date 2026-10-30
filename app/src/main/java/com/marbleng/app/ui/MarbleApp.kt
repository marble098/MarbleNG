package com.marbleng.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.NotificationRoute
import com.marbleng.app.model.DockIndicatorSpec
import com.marbleng.app.model.DockPresentation
import com.marbleng.app.model.LiquidDockNotices
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.dockShowsIcons
import com.marbleng.app.model.dockShowsLabels
import com.marbleng.app.model.dockSlotIndex
import com.marbleng.app.model.dockSlots
import com.marbleng.app.model.parseDockSize
import com.marbleng.app.model.parseDockPresentation
import com.marbleng.app.model.parseDockSlotAccent
import com.marbleng.app.model.parseDockSlotIcon
import kotlinx.coroutines.delay
import com.marbleng.app.ui.theme.LocalBottomClearance
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning
import com.marbleng.app.ui.theme.MarbleExpressiveTheme

enum class MarbleRootTab {
    HOME,
    SERVERS,
    CUSTOM_SLOT,
    SETTINGS
}

@Composable
fun MarbleApp(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit = {}
) {
    ProvideMarbleLanguage(repo.settings.appLanguage) {
        ProvideMarbleTextScale(repo.settings.textScalePercent) {
            MarbleExpressiveTheme(
                themeId = repo.settings.theme,
                accentId = repo.settings.accentTheme,
                fontId = repo.settings.fontFamily,
                flatSurfaces = repo.settings.flatInterface,
                dynamicColor = repo.settings.dynamicColor
            ) {
                MarbleRootShell(
                    repo = repo,
                    onConnect = onConnect,
                    onImportFile = onImportFile,
                    onContentScrollChanged = onContentScrollChanged
                )
            }
        }
    }
}

@Composable
private fun MarbleRootShell(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val settings = repo.settings

    var rememberedTab by rememberSaveable { mutableStateOf(MarbleRootTab.HOME) }
    var activeSettingsPage by rememberSaveable { mutableStateOf<SettingsDestination?>(null) }
    var inspectingProfileId by rememberSaveable { mutableStateOf("") }
    var inspectingProfileSource by rememberSaveable { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var showExitIpSheet by remember { mutableStateOf(false) }
    // MARBLE_EXPRESSIVE_SHELL_V241 — the bottom bar now hides while a page is scrolling, so
    // reading a long server list never happens through a strip of chrome. The pages already
    // report their scroll state; this shell is the one place that acts on it.
    var contentScrolling by remember { mutableStateOf(false) }

    val visibleTabs = dockSlots(
        all = MarbleRootTab.entries,
        optional = MarbleRootTab.CUSTOM_SLOT,
        showOptional = settings.dockSlotEnabled
    )
    val activeTab = visibleTabs[dockSlotIndex(visibleTabs, rememberedTab)]

    val detailProfile = if (inspectingProfileId.isNotBlank()) {
        repo.profile(inspectingProfileId, inspectingProfileSource)
            ?: repo.profile(inspectingProfileId)
    } else {
        null
    }

    val canNavigateBack = detailProfile != null ||
        (activeTab == MarbleRootTab.SETTINGS && activeSettingsPage != null) ||
        activeTab != MarbleRootTab.HOME

    BackHandler(enabled = canNavigateBack) {
        when {
            detailProfile != null -> {
                inspectingProfileId = ""
                inspectingProfileSource = ""
            }
            activeTab == MarbleRootTab.SETTINGS && activeSettingsPage != null -> {
                activeSettingsPage = null
            }
            else -> {
                rememberedTab = MarbleRootTab.HOME
            }
        }
    }

    LaunchedEffect(repo.message) {
        if (repo.message.isNotBlank()) {
            val currentMsg = repo.message
            delay(3_600L)
            if (repo.message == currentMsg) {
                repo.clearMessage()
            }
        }
    }

    // MARBLE_NOTIFICATION_COUNCIL_V231 — a tap on a notification names the page it belongs to; the
    // activity records it and this shell performs the navigation exactly once.
    LaunchedEffect(repo.pendingNotificationPage) {
        val routeId = repo.pendingNotificationPage
        if (routeId.isBlank()) return@LaunchedEffect
        repo.consumeNotificationPage()
        val route = NotificationRoute.fromId(routeId)
        inspectingProfileId = ""
        inspectingProfileSource = ""
        val destination = when (route) {
            NotificationRoute.DASHBOARD -> null
            NotificationRoute.SERVERS -> null
            NotificationRoute.NOTIFICATIONS -> SettingsDestination.NOTIFICATIONS_BG
            NotificationRoute.DIAGNOSTICS -> SettingsDestination.DIAGNOSTICS
            NotificationRoute.SUBSCRIPTIONS -> SettingsDestination.SUBSCRIPTIONS_DATA
        }
        rememberedTab = when {
            route == NotificationRoute.SERVERS -> MarbleRootTab.SERVERS
            destination == null -> MarbleRootTab.HOME
            else -> MarbleRootTab.SETTINGS
        }
        activeSettingsPage = destination
    }

    val reportScroll: (Boolean) -> Unit = { scrolling ->
        contentScrolling = scrolling
        onContentScrollChanged(scrolling)
    }

    val openDetail: (ProxyProfile) -> Unit = { profile ->
        inspectingProfileId = profile.id
        inspectingProfileSource = profile.subscriptionId
    }

    val openSettingsDest: (SettingsDestination) -> Unit = { dest ->
        inspectingProfileId = ""
        inspectingProfileSource = ""
        activeSettingsPage = dest
        rememberedTab = MarbleRootTab.SETTINGS
    }

    val selectTab: (MarbleRootTab) -> Unit = { chosen ->
        inspectingProfileId = ""
        inspectingProfileSource = ""
        if (chosen == MarbleRootTab.SETTINGS && activeTab == MarbleRootTab.SETTINGS) {
            activeSettingsPage = null
        }
        rememberedTab = chosen
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        // Tablets, open foldables and wide windows get a navigation rail beside a
        // capped reading column; phones keep the overlay dock. The clearance the
        // screens and the Servers FAB float above follows the chrome.
        val wide = maxWidth >= MarbleSpec.WIDE_RAIL_MIN_WIDTH_DP.dp
        CompositionLocalProvider(
            LocalBottomClearance provides
                (if (wide) MarbleSpec.RAIL_CLEARANCE_DP else MarbleSpec.BOTTOM_BAR_CLEARANCE_DP).dp
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (wide) {
                    MarbleNavigationRail(
                        repo = repo,
                        tabs = visibleTabs,
                        selected = activeTab,
                        onSelect = selectTab
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // MARBLE_LIQUID_DOCK_V245 — the page is recorded into a graphics layer so
                    // the pill above it can sample, blur and wash the pixels it actually covers.
                    // On Android 12+ that sample is what makes the glass read as glass; below it
                    // the dock keeps its wash and no layer is recorded at all.
                    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val pageLayer = rememberGraphicsLayer()
                    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                            .onGloballyPositioned { pageOrigin = it.positionInWindow() }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    if (blurSupported) {
                                        pageLayer.record(
                                            density = this,
                                            layoutDirection = layoutDirection,
                                            size = size.toIntSize()
                                        ) {
                                            this@drawWithContent.drawContent()
                                        }
                                        drawLayer(pageLayer)
                                    } else {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                        ) {
                        if (detailProfile != null) {
                            ServerDetailPage(
                                repo = repo,
                                profile = detailProfile,
                                onBack = {
                                    inspectingProfileId = ""
                                    inspectingProfileSource = ""
                                },
                                onConnect = onConnect
                            )
                        } else {
                            when (activeTab) {
                                MarbleRootTab.HOME -> HomeScreen(
                                    repo = repo,
                                    onConnect = onConnect,
                                    onOpenServers = { rememberedTab = MarbleRootTab.SERVERS },
                                    onOpenServerDetail = openDetail,
                                    onOpenQrImport = { showQrSheet = true },
                                    onOpenAddSheet = { showAddSheet = true },
                                    onOpenExitIpSheet = { showExitIpSheet = true },
                                    onOpenDiagnostics = { openSettingsDest(SettingsDestination.DIAGNOSTICS) },
                                    onOpenSettingsPage = openSettingsDest,
                                    onContentScrollChanged = reportScroll
                                )

                                MarbleRootTab.SERVERS -> ServersScreen(
                                    repo = repo,
                                    onConnect = onConnect,
                                    onOpenDetail = openDetail,
                                    onOpenAddSheet = { showAddSheet = true },
                                    onOpenQrImport = { showQrSheet = true },
                                    onContentScrollChanged = reportScroll
                                )

                                MarbleRootTab.CUSTOM_SLOT -> CustomSlotScreen(
                                    repo = repo,
                                    onConnect = onConnect,
                                    onOpenDetail = openDetail,
                                    onCustomizeSlot = { openSettingsDest(SettingsDestination.TAB_BAR) },
                                    onOpenDiagnostics = { openSettingsDest(SettingsDestination.DIAGNOSTICS) },
                                    onContentScrollChanged = reportScroll
                                )

                                MarbleRootTab.SETTINGS -> SettingsScreen(
                                    repo = repo,
                                    activeDestination = activeSettingsPage,
                                    onOpenDestination = { activeSettingsPage = it },
                                    onCloseDestination = { activeSettingsPage = null },
                                    onContentScrollChanged = reportScroll
                                )
                            }
                        }
                    }

        // Fully qualified: inside the rail Row the RowScope overload would win resolution.
        androidx.compose.animation.AnimatedVisibility(
            visible = repo.message.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // MARBLE_EXPRESSIVE_SHELL_V241 — the transient message is a floating tonal pill
            // with one soft shadow; it used to carry the same hairline the rest of the app has
            // now lost.
            Box(
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceContainerHigh)
                    .clickable { repo.clearMessage() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = trx(repo.message),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!wide) {
            // Fully qualified: inside the rail Row the RowScope overload would win resolution.
            androidx.compose.animation.AnimatedVisibility(
                visible = !contentScrolling,
                enter = slideInVertically(initialOffsetY = { height -> height }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { height -> height }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MarbleBottomTabBar(
                    repo = repo,
                    tabs = visibleTabs,
                    selected = activeTab,
                    onSelect = selectTab,
                    backdrop = if (blurSupported) pageLayer else null,
                    backdropOrigin = pageOrigin
                )
            }
        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ServerAddSheet(
            repo = repo,
            onImportFile = onImportFile,
            onOpenQrImport = { showQrSheet = true },
            onDismiss = { showAddSheet = false }
        )
    }

    QrImportSourceDialogController(
        open = showQrSheet,
        repo = repo,
        onDismiss = { showQrSheet = false }
    )

    if (showExitIpSheet) {
        ExitIpDetailsSheet(
            repo = repo,
            onDismiss = { showExitIpSheet = false }
        )
    }

    repo.availableUpdate?.let { update ->
        AppUpdateAvailableDialog(
            update = update,
            onDismiss = { repo.dismissAppUpdate() }
        )
    }
}

/**
 * Wide-screen chrome: a Material 3 navigation rail carrying the same tabs, slot icon /
 * accent and status badge as the dock. Labels follow the tab-labels setting; the rail
 * always shows icons.
 */
@Composable
private fun MarbleNavigationRail(
    repo: AppRepository,
    tabs: List<MarbleRootTab>,
    selected: MarbleRootTab,
    onSelect: (MarbleRootTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val haptics = LocalHapticFeedback.current
    val s = repo.settings
    val showLabels = dockShowsLabels(s.dockShowIcons, s.dockShowLabels)
    val slot = resolveDockSlot(repo)
    val slotIcon = dockSlotMarbleGlyph(parseDockSlotIcon(s.dockSlotIcon))
    val slotColor = dockSlotColor(parseDockSlotAccent(s.dockSlotAccent))
    val statusDotColor: Color? = if (s.dockSlotShowStatusBadge) {
        when (repo.state) {
            "CONNECTED" -> colors.success
            "CONNECTING", "DISCONNECTING" -> colors.warning
            else -> null
        }
    } else {
        null
    }

    NavigationRail(
        modifier = modifier,
        containerColor = colors.surfaceContainer
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            val accent = if (tab == MarbleRootTab.CUSTOM_SLOT) slotColor else colors.primary
            val glyph = when (tab) {
                MarbleRootTab.HOME -> MarbleGlyph.HOME
                MarbleRootTab.SERVERS -> MarbleGlyph.SERVER
                MarbleRootTab.CUSTOM_SLOT -> slotIcon
                MarbleRootTab.SETTINGS -> MarbleGlyph.TUNERS
            }
            val label = when (tab) {
                MarbleRootTab.HOME -> strings.tabHome
                MarbleRootTab.SERVERS -> strings.tabLibrary
                MarbleRootTab.CUSTOM_SLOT -> trx(slot.title)
                MarbleRootTab.SETTINGS -> strings.tabSettings
            }
            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(tab)
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab == MarbleRootTab.CUSTOM_SLOT && statusDotColor != null) {
                                Badge(containerColor = statusDotColor)
                            }
                        }
                    ) {
                        MarbleGlyphIcon(
                            glyph = glyph,
                            color = if (isSelected) accent else colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                            weight = if (isSelected) 1.18f else 1f
                        )
                    }
                },
                label = if (showLabels) {
                    { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    null
                },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = colors.secondaryContainer
                )
            )
        }
    }
}

/**
 * MARBLE_LIQUID_DOCK_V245 — the bottom chrome is now [MarbleLiquidDock]: one floating frosted
 * capsule with a glass lens on top of it, replacing the tonal bar and its measured blue line.
 *
 * This function is the *adapter*, and it is deliberately thin. Everything the settings already
 * promise still holds — dock size, floating versus attached, labels on or off, icons on or off, the
 * fourth tab's own glyph and accent, the connection light — and it is translated into one
 * [MarbleDockTab] per visible tab. The only new element is the red counter: it carries the things
 * that are genuinely waiting for a tap, an update or a scan whose result is not applied.
 */
@Composable
private fun MarbleBottomTabBar(
    repo: AppRepository,
    tabs: List<MarbleRootTab>,
    selected: MarbleRootTab,
    onSelect: (MarbleRootTab) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val s = repo.settings
    val size = parseDockSize(s.dockSize)
    val floating = parseDockPresentation(s.dockSize) == DockPresentation.FLOATING
    val showIcons = dockShowsIcons(s.dockShowIcons, s.dockShowLabels)
    val showLabels = dockShowsLabels(s.dockShowIcons, s.dockShowLabels)
    val slot = resolveDockSlot(repo)
    val slotIcon = dockSlotMarbleGlyph(parseDockSlotIcon(s.dockSlotIcon))
    val slotColor = dockSlotColor(parseDockSlotAccent(s.dockSlotAccent))
    val statusDotColor: Color? = if (s.dockSlotShowStatusBadge) {
        when (repo.state) {
            "CONNECTED" -> colors.success
            "CONNECTING", "DISCONNECTING" -> colors.warning
            else -> null
        }
    } else {
        null
    }

    // The red counter is a notification, so it is fed by notifications: a published update nobody
    // has dismissed, plus the findings of the last scan that are still waiting to be applied.
    val notices = LiquidDockNotices.count(
        updateAvailable = repo.availableUpdate != null,
        unappliedFindings = repo.councilReport
            ?.takeIf { !it.applied }
            ?.findings
            ?.size
            ?: 0
    )

    val dockTabs = tabs.map { tab ->
        MarbleDockTab(
            key = tab.name,
            label = when (tab) {
                MarbleRootTab.HOME -> strings.tabHome
                MarbleRootTab.SERVERS -> strings.tabLibrary
                MarbleRootTab.CUSTOM_SLOT -> trx(slot.title)
                MarbleRootTab.SETTINGS -> strings.tabSettings
            },
            glyph = when (tab) {
                MarbleRootTab.HOME -> MarbleGlyph.HOME
                MarbleRootTab.SERVERS -> MarbleGlyph.SERVER
                MarbleRootTab.CUSTOM_SLOT -> slotIcon
                MarbleRootTab.SETTINGS -> MarbleGlyph.TUNERS
            },
            accent = if (tab == MarbleRootTab.CUSTOM_SLOT) slotColor else colors.primary,
            badgeCount = if (tab == MarbleRootTab.SETTINGS) notices else 0,
            statusDot = if (tab == MarbleRootTab.CUSTOM_SLOT) statusDotColor else null
        )
    }

    MarbleLiquidDock(
        tabs = dockTabs,
        selectedIndex = tabs.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> tabs.getOrNull(index)?.let(onSelect) },
        modifier = modifier,
        size = size,
        showIcons = showIcons,
        showLabels = showLabels,
        floating = floating,
        backdrop = backdrop,
        backdropOrigin = backdropOrigin
    )
}
