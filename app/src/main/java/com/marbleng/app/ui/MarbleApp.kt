package com.marbleng.app.ui

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.marbleng.app.model.DockSlotKind
import com.marbleng.app.model.dockSlotDefaultCaption
import com.marbleng.app.model.dockSlotIndex
import com.marbleng.app.model.dockSlots
import com.marbleng.app.model.parseDockSize
import com.marbleng.app.model.parseDockPresentation
import com.marbleng.app.model.parseDockSlotAccent
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.marbleng.app.model.MapMotion
import com.marbleng.app.ui.theme.LocalBottomClearance
import com.marbleng.app.ui.theme.MARBLE_CHROME_FLIGHT_MS
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.ProvideMarbleMotion
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.panSpec
import com.marbleng.app.ui.theme.rememberReducedMotion
import com.marbleng.app.ui.theme.settleSpec
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import com.marbleng.app.ui.theme.isTrueBlack
import kotlin.math.roundToInt

/**
 * MARBLE_IP_IDENTITY_V251 — `CUSTOM_SLOT` is `IP`. The fourth tab is no longer a shortcut the user
 * configures; it is the page that draws every address this phone holds, in every connection state.
 * The enum constant was renamed with it so no screen can keep reading a name the product no longer
 * has.
 */
enum class MarbleRootTab {
    HOME,
    SERVERS,
    IP,
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
                // MARBLE_MAP_MOTION_V249 — one motion style, ambient, under the theme that owns the
                // colour. It sits here rather than being threaded as a parameter because every
                // surface reads it and none of them owns it: the shell pans between tabs, the pill's
                // clock rolls one digit, the dock flies out of the way, and all three have to agree
                // about how long a movement takes or the app reads as several apps.
                ProvideMarbleMotion(
                    calmMotion = repo.settings.appCalmMotion,
                    systemReducedMotion = rememberReducedMotion()
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
    // MARBLE_SETTINGS_POSITION_V253 — the Settings hub's section, search and scroll live here, so
    // a title opened from the hub (or a detour to another tab) returns the reader to the exact
    // place they left, never to the top of "All".
    val settingsHubState = rememberSettingsHubState()
    var inspectingProfileId by rememberSaveable { mutableStateOf("") }
    var inspectingProfileSource by rememberSaveable { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    // MARBLE_EXPRESSIVE_SHELL_V241 — the bottom bar now hides while a page is scrolling, so
    // reading a long server list never happens through a strip of chrome. The pages already
    // report their scroll state; this shell is the one place that acts on it.
    var contentScrolling by remember { mutableStateOf(false) }

    val visibleTabs = dockSlots(
        all = MarbleRootTab.entries,
        optional = MarbleRootTab.IP,
        showOptional = settings.dockSlotEnabled
    )
    val activeTab = visibleTabs[dockSlotIndex(visibleTabs, rememberedTab)]

    val detailProfile = if (inspectingProfileId.isNotBlank()) {
        repo.profile(inspectingProfileId, inspectingProfileSource)
            ?: repo.profile(inspectingProfileId)
    } else {
        null
    }

    // MARBLE_MAP_MOTION_V249 — the last page the zoom was showing. Closing the detail clears
    // `inspectingProfileId` immediately, and the zoom-out then has to draw the page it is leaving
    // for another 380 ms; without this it would draw nothing at all.
    val lastDetailProfile = remember { mutableStateOf<ProxyProfile?>(null) }
    if (detailProfile != null) {
        lastDetailProfile.value = detailProfile
    }

    // The pan's direction is read once per composition and captured, because `transitionSpec` is not
    // a composable scope and may not reach for either of these.
    val motion = marbleMotion()
    val readingRightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl

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
        // MARBLE_EXPRESSIVE_PAGES_V251 — one ambient light under all four tabs, drawn once here
        // rather than four times inside the screens, so the wash cannot jump when a tab changes.
        MarbleAmbientBackdrop(trueBlack = isTrueBlack(settings.theme))
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
                    // MARBLE_CRYSTAL_LENS_V248 — V245's recorded page layer and blurred backdrop
                    // are retired. The capsule is frosted white by its own material, not by a
                    // rasterised copy of the page: sampling and blurring the row underneath was
                    // the source of both the milky glass and the soft typography this pass fixes.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        // MARBLE_MAP_MOTION_V249 — the four tabs are places on one map, and the
                        // detail page is a zoom into one of them.
                        //
                        // Two nested `AnimatedContent`s, one per kind of travel, because they are not
                        // the same movement. A tab change *pans*: the page you leave departs in the
                        // direction you are going and the page you arrive on comes from there, both
                        // drifting part of the width and both scaling a little, so for the length of
                        // the flight the two are on screen together and the direction of travel is
                        // legible — which is what «مثل یک نقشه» asks for. Opening a server *zooms*:
                        // the detail arrives from slightly small while the map recedes behind it, so
                        // "into" is drawn instead of described.
                        //
                        // The detail target is a Boolean and the profile it draws is remembered,
                        // because during the zoom-out `detailProfile` is already null: an exit
                        // animation with nothing to draw is a blank frame where a page used to be.
                        AnimatedContent(
                            targetState = detailProfile != null,
                            transitionSpec = {
                                val duration = MapMotion.durationMs(1)
                                val arriveScale = if (targetState) {
                                    MapMotion.DETAIL_ENTER_SCALE
                                } else {
                                    MapMotion.DETAIL_EXIT_SCALE
                                }
                                val leaveScale = if (targetState) {
                                    MapMotion.DETAIL_EXIT_SCALE
                                } else {
                                    MapMotion.DETAIL_ENTER_SCALE
                                }
                                (
                                    fadeIn(motion.settleSpec(duration)) + scaleIn(
                                        animationSpec = motion.panSpec(duration),
                                        initialScale = arriveScale
                                    )
                                    ) togetherWith (
                                    fadeOut(motion.settleSpec(duration)) + scaleOut(
                                        animationSpec = motion.panSpec(duration),
                                        targetScale = leaveScale
                                    )
                                    )
                            },
                            label = "marbleDetailZoom"
                        ) { showingDetail ->
                            val detail = detailProfile ?: lastDetailProfile.value
                            if (showingDetail && detail != null) {
                                ServerDetailPage(
                                    repo = repo,
                                    profile = detail,
                                    onBack = {
                                        inspectingProfileId = ""
                                        inspectingProfileSource = ""
                                    },
                                    onConnect = onConnect
                                )
                            } else {
                                AnimatedContent(
                                    targetState = activeTab,
                                    transitionSpec = {
                                        // The direction comes from where the two tabs *are*, never
                                        // from which tabs they are, so reordering the dock cannot
                                        // reverse the motion. It is mirrored in a right-to-left
                                        // interface: in Persian the fourth tab is on the left, and a
                                        // pan that ignores the layout direction reads as travelling
                                        // backwards.
                                        val from = visibleTabs.indexOf(initialState)
                                        val to = visibleTabs.indexOf(targetState)
                                        val direction = MapMotion.mirrored(
                                            MapMotion.direction(from, to),
                                            rightToLeft = readingRightToLeft
                                        )
                                        val duration = MapMotion.durationMs(
                                            MapMotion.distance(from, to)
                                        )
                                        if (!MapMotion.travels(direction)) {
                                            // The same tab tapped again: nothing moved, so nothing
                                            // animates. A redundant tap must not play a motion that
                                            // says "something changed".
                                            fadeIn(motion.settleSpec(duration)) togetherWith
                                                fadeOut(motion.settleSpec(duration))
                                        } else {
                                            (
                                                slideInHorizontally(
                                                    motion.panSpec(duration)
                                                ) { width ->
                                                    (width * MapMotion.arrivingOffsetFraction(direction)).roundToInt()
                                                } + fadeIn(motion.settleSpec(duration)) + scaleIn(
                                                    animationSpec = motion.panSpec(duration),
                                                    initialScale = MapMotion.ARRIVING_SCALE
                                                )
                                                ) togetherWith (
                                                slideOutHorizontally(
                                                    motion.panSpec(duration)
                                                ) { width ->
                                                    (width * MapMotion.departingOffsetFraction(direction)).roundToInt()
                                                } + fadeOut(motion.settleSpec(duration)) + scaleOut(
                                                    animationSpec = motion.panSpec(duration),
                                                    targetScale = MapMotion.DEPARTING_SCALE
                                                )
                                                )
                                        }
                                    },
                                    label = "marbleTabMap"
                                ) { tab ->
                                    when (tab) {
                                        MarbleRootTab.HOME -> HomeScreen(
                                            repo = repo,
                                            onConnect = onConnect,
                                            onOpenServers = { rememberedTab = MarbleRootTab.SERVERS },
                                            onOpenServerDetail = openDetail,
                                            onOpenQrImport = { showQrSheet = true },
                                            onOpenAddSheet = { showAddSheet = true },
                                            // MARBLE_IP_IDENTITY_V251 — the Home row used to open a
                                            // bottom sheet with the same three facts the fourth tab
                                            // now draws as a whole page. One answer, one destination.
                                            onOpenIpPage = { rememberedTab = MarbleRootTab.IP },
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

                                        MarbleRootTab.IP -> IpIdentityScreen(
                                            repo = repo,
                                            onOpenDiagnostics = { openSettingsDest(SettingsDestination.DIAGNOSTICS) },
                                            onOpenSettingsPage = openSettingsDest,
                                            onContentScrollChanged = reportScroll
                                        )

                                        MarbleRootTab.SETTINGS -> SettingsScreen(
                                            repo = repo,
                                            activeDestination = activeSettingsPage,
                                            hubState = settingsHubState,
                                            onOpenDestination = { activeSettingsPage = it },
                                            onCloseDestination = { activeSettingsPage = null },
                                            onContentScrollChanged = reportScroll
                                        )
                                    }
                                }
                            }
                        }
                    }

        // Fully qualified: inside the rail Row the RowScope overload would win resolution.
        androidx.compose.animation.AnimatedVisibility(
            visible = repo.message.isNotBlank(),
            enter = fadeIn(motion.settleSpec(MARBLE_CHROME_FLIGHT_MS)),
            exit = fadeOut(motion.settleSpec(MARBLE_CHROME_FLIGHT_MS)),
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
                // MARBLE_MAP_MOTION_V249 — the dock flies on the app's own curve and tempo instead
                // of the framework default, so it leaves at the same speed the pages travel. The
                // argument order is deliberate: `targetOffsetY` stays first.
                enter = slideInVertically(
                    initialOffsetY = { height -> height },
                    animationSpec = motion.panSpec(MARBLE_CHROME_FLIGHT_MS)
                ) + fadeIn(motion.settleSpec(MARBLE_CHROME_FLIGHT_MS)),
                exit = slideOutVertically(targetOffsetY = { height -> height }, animationSpec = motion.panSpec(MARBLE_CHROME_FLIGHT_MS)) +
                    fadeOut(motion.settleSpec(MARBLE_CHROME_FLIGHT_MS)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MarbleBottomTabBar(
                    repo = repo,
                    tabs = visibleTabs,
                    selected = activeTab,
                    onSelect = selectTab
                )
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
    // MARBLE_IP_IDENTITY_V251 — the fourth tab has a fixed name and glyph now: it is the IP page,
    // not a shortcut whose identity the user picks. The slot's own colour still applies, because
    // that is appearance, not meaning.
    val ipLabel = trx(dockSlotDefaultCaption(DockSlotKind.IP, ""))
    val ipGlyph = MarbleGlyph.GLOBE
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
            val accent = if (tab == MarbleRootTab.IP) slotColor else colors.primary
            val glyph = when (tab) {
                MarbleRootTab.HOME -> MarbleGlyph.HOME
                MarbleRootTab.SERVERS -> MarbleGlyph.SERVER
                MarbleRootTab.IP -> ipGlyph
                MarbleRootTab.SETTINGS -> MarbleGlyph.TUNERS
            }
            val label = when (tab) {
                MarbleRootTab.HOME -> strings.tabHome
                MarbleRootTab.SERVERS -> strings.tabLibrary
                MarbleRootTab.IP -> ipLabel
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
                            if (tab == MarbleRootTab.IP && statusDotColor != null) {
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
 * MARBLE_LIQUID_DOCK_V245 / MARBLE_CRYSTAL_LENS_V248 — the bottom chrome is now
 * [MarbleLiquidDock]: one floating frosted capsule with a crystal lens travelling across it,
 * replacing the tonal bar and its measured blue line.
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
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val s = repo.settings
    val size = parseDockSize(s.dockSize)
    val floating = parseDockPresentation(s.dockSize) == DockPresentation.FLOATING
    val showIcons = dockShowsIcons(s.dockShowIcons, s.dockShowLabels)
    val showLabels = dockShowsLabels(s.dockShowIcons, s.dockShowLabels)
    val ipLabel = trx(dockSlotDefaultCaption(DockSlotKind.IP, ""))
    val ipGlyph = MarbleGlyph.GLOBE
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
                MarbleRootTab.IP -> ipLabel
                MarbleRootTab.SETTINGS -> strings.tabSettings
            },
            glyph = when (tab) {
                MarbleRootTab.HOME -> MarbleGlyph.HOME
                MarbleRootTab.SERVERS -> MarbleGlyph.SERVER
                MarbleRootTab.IP -> ipGlyph
                MarbleRootTab.SETTINGS -> MarbleGlyph.TUNERS
            },
            accent = if (tab == MarbleRootTab.IP) slotColor else colors.primary,
            badgeCount = if (tab == MarbleRootTab.SETTINGS) notices else 0,
            statusDot = if (tab == MarbleRootTab.IP) statusDotColor else null
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
        floating = floating
    )
}
