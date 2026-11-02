@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.coreEngine
import com.marbleng.app.model.ConnectModeChip
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.HomePingAction
import com.marbleng.app.model.HeroMetricKind
import com.marbleng.app.model.HomeHeroSnapshot
import com.marbleng.app.model.HomeHeroStudio
import com.marbleng.app.model.HomeSection
import com.marbleng.app.model.HomeStudio
import com.marbleng.app.model.IpAddressBook
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.ServerHealth
import com.marbleng.app.model.applyTo
import com.marbleng.app.model.connectModeChipOf
import com.marbleng.app.model.parseHomePingAction
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.MARBLE_CHROME_FLIGHT_MS
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.panSpec
import com.marbleng.app.ui.theme.settleSpec
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

/**
 * MARBLE_EXPRESSIVE_SHELL_V241 + MARBLE_WEIGHT_LADDER_V235 + MARBLE_EXPRESSIVE_STUDIO_V252:
 *
 * The Material 3 Expressive Home screen. Non-Expressive borders, plain circles, spreadsheet grid
 * lines, and four-row vertical lists are replaced with official `MaterialShapes` polygons,
 * spring-driven `Morph` transitions, asymmetric tonal Bento cards, and Google's Expressive
 * `LoadingIndicator` for live probes.
 */
@Composable
fun HomeScreen(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onOpenServers: () -> Unit,
    onOpenServerDetail: (ProxyProfile) -> Unit,
    onOpenQrImport: () -> Unit,
    onOpenAddSheet: () -> Unit,
    onOpenIpPage: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettingsPage: (SettingsDestination) -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tones = marbleExpressiveTones()
    val strings = Tr.now
    val settings = repo.settings
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    val state = repo.state
    val connected = state == "CONNECTED"
    val connecting = state == "CONNECTING"
    val disconnecting = state == "DISCONNECTING"
    val activeRoute = repo.homeRoute()
    val uptime = rememberUptimeLabel(repo.connectedSinceMs)

    val modeLabel = if (settings.connectionMode == ConnectionMode.FULL_TUN) {
        trx("Full TUN")
    } else {
        "${trx("Local Proxy")} :${settings.localProxyPort}"
    }
    val coreLabel = if (settings.coreEngine() == CoreEngine.SINGBOX) "sing-box" else "Xray"
    val headerSubtitle = "$modeLabel · $coreLabel"

    val toggleConnection: () -> Unit = {
        when {
            connected || connecting -> repo.stopVpn()
            !disconnecting -> repo.reconnectLastOrAuto(onConnect)
        }
    }

    var chosenChipId by rememberSaveable { mutableStateOf("") }
    val modeChip = if (chosenChipId.isBlank()) {
        connectModeChipOf(settings.connectionMode)
    } else {
        ConnectModeChip.fromId(chosenChipId)
    }
    val selectModeChip: (ConnectModeChip) -> Unit = { chip ->
        chosenChipId = chip.id
        if (chip != ConnectModeChip.SMART) {
            repo.setConnectionMode(chip.applyTo(settings).connectionMode)
        } else {
            repo.setRuntimeMessage(
                "Smart mode • the ranker picks the best measured route"
            )
        }
    }
    var showQuickLogs by rememberSaveable { mutableStateOf(false) }

    val triggerHomePing: () -> Unit = {
        if (repo.probeActive || repo.probeCancelling) {
            repo.cancelProbes()
        } else {
            when (parseHomePingAction(settings.homePingGaugeAction)) {
                HomePingAction.ROUTE -> repo.measureHomePing()
                HomePingAction.GROUP -> repo.pingHomeGroup()
                HomePingAction.LIBRARY -> repo.testAll()
                HomePingAction.TESTS -> onOpenSettingsPage(SettingsDestination.PING_TESTING)
            }
        }
    }

    val pasteFromClipboard: () -> Unit = {
        val raw = clipboard.getText()?.text?.trim().orEmpty()
        if (raw.isBlank()) {
            repo.setRuntimeMessage(strings.clipboardNothingFound)
        } else {
            repo.importClipboard(raw, repo.intakeTargetOrManual(repo.librarySourceFilter))
        }
    }

    val addDesc = trx("Add Server")
    val diagDesc = trx("Fix & diagnose")

    // ── MARBLE_HOME_HERO_V261 ─────────────────────────────────────────────────────────────────
    //
    // The hero is a *value* before it is a layout: one snapshot built here, from the repository and
    // the two formatters the page already owns, and handed to a composable that only paints it.
    // That is what keeps the four facts consistent — every one of them goes through the same
    // "measured / measuring / nothing to measure" decision in `HomeHeroStudio`, so the panel can
    // never print `0 ms` where it means "no probe has run".
    val heroRouteName = activeRoute?.let { cleanServerName(it.name).ifBlank { it.host } }
        ?: strings.chooseRoute
    val heroRouteDetail = if (activeRoute != null) {
        listOf(protocolShortLabel(activeRoute), activeRoute.subscriptionName)
            .filter { it.isNotBlank() }
            .joinToString(" \u00b7 ")
    } else {
        trx("Tap to pick a server")
    }
    // MARBLE_LIVE_TICKER_V249 — the clock stays a rolling reading, never a re-laid-out string: the
    // same `faDigits(uptime)` value the ticker has always been handed, now carried by the hero's
    // snapshot instead of only by the connect pill's `ticker` parameter.
    val ticker = if (connected) faDigits(uptime) else ""
    val heroUptime = ticker
    val heroExit = repo.ipStackReport?.exit?.address?.takeIf { it.isNotBlank() }
        ?: repo.serverIntel?.ip?.takeIf { it.isNotBlank() }
        ?: ""
    // The jitter of a route nobody has dialled lives in the last benchmark of that route, if one
    // was ever run. Reading it here keeps the model free of the repository.
    val heroBenchmarkedJitter = activeRoute?.let { profile ->
        repo.benchmarks.lastOrNull { it.profileId == profile.id }?.jitterMs
    }
    val heroPingMs = HomeHeroStudio.ping(
        connected = connected,
        livePingMs = repo.livePingMs,
        selectedPingMs = repo.selectedPingMs
    )
    // The jitter *is* measurable before a connection exists — `measureHomePing` stores it on the
    // benchmark — so the tile shows a dash while that probe runs rather than a confident dash
    // afterwards. The ping cell owns the "measuring" animation; jitter waits its turn in the same
    // probe, and saying so is what keeps the panel honest.
    val heroPingRunning = repo.probeActive ||
        (if (connected) repo.connectionPingState else repo.selectedPingState) ==
        ConnectionPingState.MEASURING
    val heroJitterMs = HomeHeroStudio.jitter(
        connected = connected,
        liveJitterMs = repo.liveJitterMs,
        benchmarkedJitterMs = heroBenchmarkedJitter
    )
    val heroWorking = heroPingRunning
    val heroSnapshot = HomeHeroSnapshot(
        stateLabel = headlineFor(connected, connecting, disconnecting),
        overline = trx("Connection"),
        routeName = heroRouteName,
        routeDetail = heroRouteDetail,
        countryCode = activeRoute?.let { repo.serverLocation(it).code },
        metrics = HomeHeroStudio.metrics(
            connected = connected,
            working = heroWorking,
            pingMs = heroPingMs,
            jitterMs = heroJitterMs,
            uptimeLabel = heroUptime,
            exitAddress = heroExit,
            hasRoute = activeRoute != null
        ),
        working = heroWorking
    )

    val homeSections = HomeStudio.visible(
        orderRaw = settings.homeLayout.sectionOrder,
        hiddenRaw = settings.homeLayout.hiddenSections,
        legacyStatsVisible = settings.modularShowStats,
        legacyShortcutsVisible = settings.modularShowShortcuts
    )

    // MARBLE_HOME_DECLUTTER_V260 — the warm-up is the page's only "what am I drawing" answer.
    //
    // V259 also drew a *quiet card* here: a 3/6 counter, a line about only what you need, and a pair
    // of buttons that expanded the page or opened the section switches. It was reported and it was
    // right — the page that is supposed to be the calm one ended with a card explaining its own
    // modesty, and a counter is the last thing a reader needs on a screen with three objects on it.
    // The way back is the Settings page that already owns the switches, so the card, its copy and
    // its two verbs are gone, and Home no longer imports the layout model at all.
    val showWarmup = homeShowsWarmup(repo.profiles.size)

    // MARBLE_HOME_WIDGETS_V256 — Home Settings. `HomeStudio` owns *which sections exist*; these four
    // switches own *what is inside the ones that stay*, and both are honoured in the same place so
    // the page cannot end up half-built.
    val widgets = settings.homeLayout
    val widgetStyle = widgets.widgetStyle
    val motion = marbleMotion()
    val appear = fadeIn(motion.settleSpec(MARBLE_CHROME_FLIGHT_MS)) +
        expandVertically(animationSpec = motion.panSpec(MARBLE_CHROME_FLIGHT_MS))
    val vanish = fadeOut(motion.settleSpec(MARBLE_CHROME_FLIGHT_MS)) +
        shrinkVertically(animationSpec = motion.panSpec(MARBLE_CHROME_FLIGHT_MS))

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            top = 8.dp,
            bottom = MarbleTheme.bottomClearance
        ),
        verticalArrangement = Arrangement.spacedBy(MarbleSpec.SECTION_SPACING_DP.dp)
    ) {
        homeSections.forEach { section ->
            when (section) {
                HomeSection.HEADER -> item(key = "home-header") {
                    MarbleLargeHeader(
                        title = "MarbleNG",
                        subtitle = headerSubtitle,
                        collapseFraction = collapseFractionOf(
                            firstVisibleItemIndex = listState.firstVisibleItemIndex,
                            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
                        )
                    ) {
                        MarbleExpressiveIconButton(
                            glyph = MarbleGlyph.SHIELD,
                            motif = MarbleExpressiveMotif.ARCH,
                            containerColor = tones.plum.container,
                            contentColor = if (connected) colors.success else colors.onSurfaceVariant,
                            contentDescription = diagDesc,
                            onClick = onOpenDiagnostics
                        )
                        MarbleExpressiveIconButton(
                            glyph = MarbleGlyph.PLUS,
                            motif = MarbleExpressiveMotif.CLOVER,
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                            prominent = true,
                            contentDescription = addDesc,
                            onClick = onOpenAddSheet
                        )
                    }
                }

                HomeSection.CONNECT -> item(key = "home-hero") {
                    // MARBLE_HOME_QUIET_V259 — while the library is empty, the page's first job is
                    // "get one server in here", not "read a dashboard". Two tonal keys, directly
                    // under the heading and above the stage, for as long as the library is empty.
                    if (showWarmup) {
                        Column(
                            modifier = Modifier.padding(bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // MARBLE_HOME_DECLUTTER_V260 — three tonal keys, and two of them were
                            // the header's own + button and the Add sheet's own first row. The
                            // warm-up now offers exactly what the page does *not* already offer
                            // for free: the clipboard it already holds, and the code it can read.
                            MarbleWarmupRow(
                                onPaste = pasteFromClipboard,
                                onScan = onOpenQrImport
                            )
                        }
                    }

                    // MARBLE_HOME_HERO_V261 — one hero for every connection style.
                    //
                    // V259/V260 drew two: `HomeHero` for Style C and `HomeDockedStage` for the two
                    // styles that keep the connect control in the dock. They differed in *where a
                    // button was*, and the brief's own reading of the page ("better, more flexible,
                    // and carrying the essentials") is not a request for a third layout — it is a
                    // request for one surface that says the same four things either way, and puts
                    // the connect control inside itself only when the dock does not have it.
                    HomeHeroStage(
                        snapshot = heroSnapshot,
                        modeChip = modeChip,
                        onSelectMode = selectModeChip,
                        connectInPanel = widgetStyle.showsHomeHero,
                        connected = connected,
                        connecting = connecting,
                        disconnecting = disconnecting,
                        uptime = uptime,
                        ticker = ticker,
                        routeLabel = heroRouteName,
                        onToggle = toggleConnection,
                        onMetricClick = { kind ->
                            when (kind) {
                                HeroMetricKind.PING -> triggerHomePing()
                                HeroMetricKind.JITTER -> triggerHomePing()
                                HeroMetricKind.UPTIME -> showQuickLogs = true
                                HeroMetricKind.EXIT -> onOpenIpPage()
                            }
                        },
                        onOpenLogs = { showQuickLogs = true },
                        onOpenRoute = onOpenServers
                    )
                }

                HomeSection.PRESETS -> item(key = "home-presets") {
                    AnimatedVisibility(
                        visible = widgets.showPresets,
                        enter = appear,
                        exit = vanish
                    ) {
                        SmartPresetStrip(
                            repo = repo,
                            onOpenAll = { onOpenSettingsPage(SettingsDestination.SMART_PRESETS) }
                        )
                    }
                }

                HomeSection.ROUTE -> item(key = "home-route-section") {
                    // Two of the four switches live here: the route strip and the exit-IP box are
                    // separate answers to separate questions, so they are two gates, not one.
                    AnimatedVisibility(
                        visible = widgets.showRouteCard || widgets.showIpBox,
                        enter = appear,
                        exit = vanish
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // MARBLE_HOME_DECLUTTER_V260 — no section label above the route card.
                            // The card's own first line *is* the server's name, and a heading that
                            // says "Active Route" over a card that says "Berlin 01" is the page
                            // answering one question twice.
                            HomeRouteAndIdentityCard(
                                repo = repo,
                                activeRoute = activeRoute,
                                connected = connected,
                                showRoute = widgets.showRouteCard,
                                showIpBox = widgets.showIpBox,
                                onOpenServers = onOpenServers,
                                onOpenServerDetail = onOpenServerDetail,
                                onTriggerPing = triggerHomePing,
                                onOpenIpPage = onOpenIpPage
                            )
                        }
                    }
                }

                HomeSection.STATS -> item(key = "home-telemetry-section") {
                    AnimatedVisibility(
                        visible = widgets.showSpeedGraph,
                        enter = appear,
                        exit = vanish
                    ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarbleSectionHeader(title = strings.networkSpeed)
                        HomeTelemetryCard(
                            downBps = repo.liveDownBps,
                            upBps = repo.liveUpBps,
                            live = connected,
                            sessionBytes = repo.sessionBytes,
                            totalBytes = repo.totalUsageBytes,
                            showSocks = settings.connectionMode == ConnectionMode.LOCAL_PROXY || settings.modularShowSocks,
                            socksPort = settings.localProxyPort,
                            onCopySocks = {
                                clipboard.setText(AnnotatedString("127.0.0.1:${settings.localProxyPort}"))
                                repo.setRuntimeMessage(strings.socksCopied)
                            }
                        )
                    }
                    }
                }

                HomeSection.SHORTCUTS -> item(key = "home-shortcuts-section") {
                    // MARBLE_HOME_DECLUTTER_V260 — the row shrank from four tiles to the two that
                    // have nowhere else to live.
                    //
                    // "Paste a link" was the four-tile row's first key and the warm-up's first key,
                    // drawn in the same place on the same page; it survives in the warm-up, which
                    // only exists while the library is empty — the moment it is the *only* way in.
                    // "Scan QR" is the header's own + sheet and the camera; the warm-up keeps it too.
                    // What is left is the two verbs that are this page's own: rank the library, and
                    // run the network lab. They are the two things a reader on Home wants to *do*
                    // about the connection they are looking at.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarbleSectionHeader(title = trx("Quick Actions"))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            HomeQuickTile(
                                title = trx("Smart Rank"),
                                subtitle = trx("Find the fastest route"),
                                glyph = MarbleGlyph.SPARK,
                                motif = MarbleExpressiveMotif.SUNNY,
                                tint = colors.warning,
                                enabled = !repo.busy && !repo.probeActive,
                                onClick = { repo.smartRank() },
                                modifier = Modifier.weight(1f)
                            )
                            HomeQuickTile(
                                title = trx("Censorship Lab"),
                                subtitle = trx("Test the network"),
                                glyph = MarbleGlyph.SHIELD,
                                motif = MarbleExpressiveMotif.ARCH,
                                tint = colors.success,
                                onClick = { onOpenSettingsPage(SettingsDestination.CENSORSHIP_LAB) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

    }

    if (showQuickLogs) {
        QuickLogsSheet(
            repo = repo,
            onDismiss = { showQuickLogs = false }
        )
    }
}

/**
 * MARBLE_HOME_HERO_V261 — the one hero, for every connection style.
 *
 * V259 and V260 drew two of these. `HomeDockedStage` printed one big number and put the mode chip and
 * a logs capsule underneath it; `HomeHero` printed the clock and put the connect pill inside itself.
 * They were the *same panel* differing in one boolean — is the connect control here or in the dock? —
 * and the brief's request ("better, more flexible, more Material, carrying ping, jitter, uptime,
 * server and IP") is answered by making the panel carry all five *always* and letting the connect
 * control be the one thing that moves.
 *
 * So this composable takes a [HomeHeroSnapshot] and adds exactly one decision:
 *
 *  - [connectInPanel] true (the reader chose Style C): the stage's primary action is the connect pill,
 *    and the mode chip plus the route pill sit under the panel, because two rows of controls inside
 *    one surface is the crowding the redesign removed.
 *  - [connectInPanel] false (styles A and B): the connect control *is* the dock, so the panel draws
 *    the mode chip and its logs capsule in that row instead, and never a second button for an action
 *    the reader can already reach with their thumb.
 *
 * The metric cells are the panel's own four answers, and each one is its own target: ping and jitter
 * re-run the probe, the uptime clock opens the logs, and the exit address opens the IP page. The route
 * strip under them opens the server list, because "connected to what?" and "where does it exit?" are
 * two questions.
 */
@Composable
private fun HomeHeroStage(
    snapshot: HomeHeroSnapshot,
    modeChip: ConnectModeChip,
    onSelectMode: (ConnectModeChip) -> Unit,
    connectInPanel: Boolean,
    connected: Boolean,
    connecting: Boolean,
    disconnecting: Boolean,
    uptime: String,
    ticker: String,
    routeLabel: String,
    onToggle: () -> Unit,
    onMetricClick: (HeroMetricKind) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenRoute: () -> Unit
) {
    val strings = Tr.now
    val working = connecting || disconnecting

    MarbleHeroPanel(
        snapshot = snapshot,
        motif = MarbleExpressiveMotif.CLOVER,
        onMetricClick = onMetricClick,
        onOpenLogs = onOpenLogs,
        onOpenRoute = onOpenRoute,
        tag = if (connectInPanel) "home-hero-stage" else "home-calm-stage",
        primaryAction = if (!connectInPanel) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarbleModeMenuChip(
                        selected = modeChip,
                        onSelect = onSelectMode,
                        enabled = !disconnecting
                    )
                    // The two things a reader reaches for while looking at four numbers are "what
                    // mode am I in" and "why", and both belong under the numbers rather than in the
                    // page's corner. MARBLE_HOME_HERO_V261 — the logs capsule used to be described
                    // to a screen reader as "Protected" while it visibly read "Logs"; one object,
                    // two names. It is now described by the name it prints.
                    HomeStatusChip(
                        connected = connected,
                        working = working,
                        onClick = onOpenLogs,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            null
        }
    )

    if (connectInPanel) {
        // Style C — the pill already owns the panel's bottom edge, so this row is *outside* it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarbleModeMenuChip(
                selected = modeChip,
                onSelect = onSelectMode,
                enabled = !disconnecting
            )
            // The connect verb, once, and the route it will use: the pill is the only connect
            // control on this page and the route strip inside the panel is a reading.
            MarbleConnectPill(
                connected = connected,
                connecting = connecting,
                disconnecting = disconnecting,
                title = if (connected) strings.disconnect else strings.connect,
                subtitle = routeLabel,
                onToggle = onToggle,
                // The rolling clock, handed in exactly as V249 pinned it: eight animating digits in
                // one `Text`, not a string rebuilt every second.
                ticker = ticker,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Expressive status + core-log capsule in the hero header. */
@Composable
private fun HomeStatusChip(
    connected: Boolean,
    working: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val caption = trx("Logs")
    val health = when {
        connected -> ServerHealth.GOOD
        working -> ServerHealth.SLOW
        else -> ServerHealth.UNTESTED
    }
    val shape = marbleSelectionMorphShape(
        selected = connected,
        idleDp = 16.dp,
        selectedDp = 26.dp
    )
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = caption }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarbleHealthDot(health = health, size = 12.dp)
        Text(
            text = caption,
            color = colors.onSurface,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

/**
 * MARBLE_HOME_QUIET_V259 — the stage's one number, as a string.
 *
 * Four states, in the order a reader cares about them: a live measurement, a failure that has a
 * reason, a measurement that is running, and "nothing measured yet". The previous stage spread these
 * four across a chip, a subtitle and a caption and never said which one was the answer.
 */
private fun pingStageLabel(repo: AppRepository, connected: Boolean): String {
    val state = if (connected) repo.connectionPingState else repo.selectedPingState
    val latency = if (connected) repo.connectionPingMs else repo.selectedPingMs
    val failure = if (connected) repo.connectionPingFailure else repo.selectedPingFailure
    val live = if (connected) repo.livePingMs else 0
    return when {
        repo.probeActive || state == ConnectionPingState.MEASURING -> "\u2026"
        state == ConnectionPingState.FAILED -> failure.ifBlank { "\u2014" }
        latency > 0 -> "$latency ms"
        live > 0 -> "$live ms"
        else -> "\u2014"
    }
}

internal fun headlineFor(connected: Boolean, connecting: Boolean, disconnecting: Boolean): String =
    when {
        connected -> "Protected"
        connecting -> "Securing the route"
        disconnecting -> "Closing the route"
        else -> "Ready when you are"
    }

@Composable
private fun HomeQuickTile(
    title: String,
    glyph: MarbleGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    motif: MarbleExpressiveMotif = MarbleExpressiveMotif.forGlyph(glyph),
    subtitle: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    MarbleExpressiveTile(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        motif = motif,
        tint = tint,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        tag = "home-quick-$title"
    )
}

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 — Expressive Route & Identity Bento.
 *
 * Replaces the four-row vertical divider list with a tonal active-server card above a balanced
 * two-column Expressive Bento pair for Ping (`Sunny` / `LoadingIndicator`) and Exit IP (`Clover`).
 */
@Composable
private fun HomeRouteAndIdentityCard(
    repo: AppRepository,
    activeRoute: ProxyProfile?,
    connected: Boolean,
    showRoute: Boolean = true,
    showIpBox: Boolean = true,
    onOpenServers: () -> Unit,
    onOpenServerDetail: (ProxyProfile) -> Unit,
    onTriggerPing: () -> Unit,
    onOpenIpPage: () -> Unit
) {
    val tones = marbleExpressiveTones()
    val motion = marbleMotion()
    val strings = Tr.now
    val countryCode = activeRoute?.let { repo.serverLocation(it).code }

    val pingState = if (connected) repo.connectionPingState else repo.selectedPingState
    val pingMs = if (connected) repo.connectionPingMs else repo.selectedPingMs
    val pingFailure = if (connected) repo.connectionPingFailure else repo.selectedPingFailure
    val isMeasuring = pingState == ConnectionPingState.MEASURING || repo.probeActive

    val pingHeadline = when {
        isMeasuring -> strings.measuring
        pingState == ConnectionPingState.MEASURED && pingMs > 0 -> faDigits("$pingMs ms")
        pingState == ConnectionPingState.FAILED -> pingFailure.ifBlank { strings.pingFailed }
        repo.livePingMs > 0 && connected -> faDigits("${repo.livePingMs} ms")
        else -> strings.testPing
    }

    val methodName = when (repo.settings.probeMethod) {
        ProbeMethod.REAL_DELAY -> trx("Real delay")
        ProbeMethod.TCP_PING -> trx("TCP ping")
        ProbeMethod.URL_TEST -> trx("URL test")
    }
    val pingSub = buildString {
        append(methodName)
        if (connected && repo.liveJitterMs > 0) {
            append(" · ")
            append(trx("Jitter"))
            append(" ${faDigits("${repo.liveJitterMs} ms")}")
        }
        if (repo.probeActive) {
            append(" · ")
            append(trx("Tap to stop"))
        }
    }

    val pingPair = when {
        pingState == ConnectionPingState.MEASURED && pingMs in 1..180 -> tones.mint
        pingState == ConnectionPingState.MEASURED && pingMs > 180 -> tones.amber
        else -> tones.plum
    }

    val ipReport = repo.ipStackReport
    val intel = repo.serverIntel
    val ipTitle = when {
        repo.ipStackScanning && ipReport?.exit?.discovered != true -> strings.resolving
        ipReport?.exit?.address?.isNotBlank() == true -> ipReport.exit!!.address
        intel != null && intel.ip.isNotBlank() -> intel.ip
        else -> strings.ipAddress
    }
    val ipSubtitle = when {
        ipReport?.exit?.discovered == true -> {
            val detailed = IpAddressBook.egressDetail(ipReport.exit)
            detailed.ifBlank { trx("Public exit address") }
        }
        intel != null -> listOf(intel.locationLabel, intel.datacenterLabel)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { trx("Tap to open the IP page") }
        repo.serverIntelError.isNotBlank() -> trx(repo.serverIntelError)
        else -> trx("Tap to open every address on this phone")
    }
    val inspectDesc = trx("Inspect Server Configuration")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = showRoute,
            enter = fadeIn(motion.settleSpec(MARBLE_CHROME_FLIGHT_MS)) +
                expandVertically(animationSpec = motion.panSpec(MARBLE_CHROME_FLIGHT_MS)),
            exit = fadeOut(motion.settleSpec(MARBLE_CHROME_FLIGHT_MS)) +
                shrinkVertically(animationSpec = motion.panSpec(MARBLE_CHROME_FLIGHT_MS))
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(marbleAsymmetricShape(prominent = false))
                .background(tones.sky.container)
                .clickable(role = Role.Button, onClick = onOpenServers)
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MarbleServerAvatar(
                profile = activeRoute,
                countryCode = countryCode,
                size = 46.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = activeRoute?.let { cleanServerName(it.name) } ?: strings.chooseRoute,
                    color = tones.sky.content,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (activeRoute != null) {
                        "${protocolShortLabel(activeRoute)} · ${activeRoute.subscriptionName}"
                    } else {
                        trx("Select or import a server from the Servers tab")
                    },
                    color = tones.sky.content.copy(alpha = 0.80f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (activeRoute != null) {
                MarbleActionColumn {
                    MarbleExpressiveIconButton(
                        glyph = MarbleGlyph.TUNERS,
                        motif = MarbleExpressiveMotif.FLOWER,
                        containerColor = tones.sky.content.copy(alpha = 0.12f),
                        contentColor = tones.sky.content,
                        contentDescription = inspectDesc,
                        onClick = { onOpenServerDetail(activeRoute) }
                    )
                }
            } else {
                MarbleChevron(size = 18.dp, tint = tones.sky.content)
            }
        }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeMetricBentoTile(
                overline = strings.connectionPing,
                value = pingHeadline,
                subtitle = pingSub,
                glyph = MarbleGlyph.GAUGE,
                motif = MarbleExpressiveMotif.SUNNY,
                pair = pingPair,
                loading = isMeasuring,
                monospaceValue = !isMeasuring && (pingMs > 0 || (connected && repo.livePingMs > 0)),
                flipped = false,
                onClick = onTriggerPing,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            if (showIpBox) HomeMetricBentoTile(
                overline = trx("Exit IP"),
                value = ipTitle,
                subtitle = ipSubtitle,
                glyph = MarbleGlyph.GLOBE,
                motif = MarbleExpressiveMotif.CLOVER,
                pair = tones.lavender,
                loading = repo.ipStackScanning && ipReport?.exit?.discovered != true,
                monospaceValue = ipReport?.exit?.address?.isNotBlank() == true || (intel != null && intel.ip.isNotBlank()),
                flipped = true,
                onClick = {
                    if (ipReport == null && !repo.ipStackScanning) {
                        repo.refreshIpStack()
                    }
                    onOpenIpPage()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun HomeMetricBentoTile(
    overline: String,
    value: String,
    subtitle: String,
    glyph: MarbleGlyph,
    motif: MarbleExpressiveMotif,
    pair: MarbleExpressivePair,
    loading: Boolean,
    monospaceValue: Boolean,
    flipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = marbleMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) MarbleSpec.PRESSED_SCALE else 1f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "homeMetricPress"
    )
    val shape = marbleAsymmetricShape(
        prominent = false,
        pressed = pressed,
        flipped = flipped
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(pair.container)
            .clickable(
                role = Role.Button,
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(marbleExpressiveShape(motif))
                    .background(pair.badge.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    LoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = pair.badge
                    )
                } else {
                    MarbleGlyphIcon(
                        glyph = glyph,
                        color = pair.badge,
                        modifier = Modifier.size(19.dp),
                        weight = 1.12f
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = overline,
                color = pair.content.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                color = pair.content,
                style = MaterialTheme.typography.titleMediumEmphasized.copy(
                    fontFamily = if (monospaceValue) FontFamily.Monospace else FontFamily.Default
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = pair.content.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * MARBLE_HOME_DECLUTTER_V260 — the traffic block, as one graph and one line of readings.
 *
 * The previous card said the same four numbers twice. The sparkline carried a download figure and an
 * upload figure in its legend *and* drew them; under it, two 96dp tiles repeated those two figures in
 * display type, and under *those*, two more tiles printed the session and the lifetime totals. On a
 * phone that is three answers to "how fast" stacked in one card, which is why the page felt long
 * without feeling full.
 *
 * The graph keeps the shape of the traffic — the only thing a graph can say that a number cannot —
 * and every number it used to shout is now one row of readings underneath it: down, up, this session,
 * all time. Nothing was deleted from the page except the repetition; the SOCKS address stays, because
 * it is the one thing in this card a reader *acts* on.
 */
@Composable
private fun HomeTelemetryCard(
    downBps: Long,
    upBps: Long,
    live: Boolean,
    sessionBytes: Long,
    totalBytes: Long,
    showSocks: Boolean,
    socksPort: Int,
    onCopySocks: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(marbleAsymmetricShape(prominent = true))
            .background(colors.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MarbleTrafficSparkline(
            downBps = downBps,
            upBps = upBps,
            active = live,
            height = MarbleSpec.SPARKLINE_HEIGHT_DP.dp
        )

        // One row, four readings, equal weight: the graph above says *when*, this says *how much*.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HomeReading(
                value = faDigits(formatRate(downBps)),
                label = strings.download,
                tint = colors.primary,
                glyph = MarbleGlyph.DOWNLOAD,
                modifier = Modifier.weight(1f)
            )
            HomeReading(
                value = faDigits(formatRate(upBps)),
                label = strings.upload,
                tint = colors.tertiary,
                glyph = MarbleGlyph.UPLOAD,
                modifier = Modifier.weight(1f)
            )
            HomeReading(
                value = faDigits(formatBytes(sessionBytes)),
                label = trx("Session"),
                tint = colors.secondary,
                glyph = MarbleGlyph.LAYERS,
                modifier = Modifier.weight(1f)
            )
            HomeReading(
                value = faDigits(formatBytes(totalBytes)),
                label = trx("Total"),
                tint = colors.onSurfaceVariant,
                glyph = MarbleGlyph.VAULT,
                modifier = Modifier.weight(1f)
            )
        }

        if (showSocks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceContainer)
                    .clickable(role = Role.Button, onClick = onCopySocks)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(marbleExpressiveShape(MarbleExpressiveMotif.DIAMOND))
                        .background(colors.secondary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.LINK,
                        color = colors.secondary,
                        modifier = Modifier.size(16.dp),
                        weight = 1.1f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${strings.socksProxyLabel} · 127.0.0.1:$socksPort",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = trx("Tap to copy local SOCKS5 address"),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = strings.copyAction,
                    color = colors.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

/**
 * One reading in the traffic card's row: a small tonal glyph, the number, the word.
 *
 * Deliberately *not* a tile. Four bordered tiles read as four destinations; four readings separated
 * by air read as one sentence — and this card is a reading, so nothing in it competes with the
 * connect control for attention.
 */
@Composable
private fun HomeReading(
    value: String,
    label: String,
    tint: Color,
    glyph: MarbleGlyph,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(marbleAsymmetricShape(prominent = false))
            .background(tint.copy(alpha = 0.09f))
            .padding(horizontal = 9.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MarbleGlyphIcon(
            glyph = glyph,
            color = tint,
            modifier = Modifier.size(14.dp),
            weight = 1.1f
        )
        Text(
            text = value,
            color = colors.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

