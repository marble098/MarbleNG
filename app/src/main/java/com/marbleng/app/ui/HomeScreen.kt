package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.coreEngine
import com.marbleng.app.model.ConnectModeChip
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.HomePingAction
import com.marbleng.app.model.HomeSection
import com.marbleng.app.model.HomeStudio
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.ServerHealth
import com.marbleng.app.model.applyTo
import com.marbleng.app.model.connectModeChipOf
import com.marbleng.app.model.parseHomePingAction
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.MarbleCardShape
import com.marbleng.app.ui.theme.marbleDefaultEffectsSpec
import com.marbleng.app.ui.theme.onSuccessContainer
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.successContainer
import com.marbleng.app.ui.theme.warning

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

    // MARBLE_EXPRESSIVE_SHELL_V241 — one control, one decision.
    //
    // The old Home asked the user to pick between five ways to connect (a power orb, a slide
    // track and three deck cards). The pill above owns the action; this chip owns the *mode*
    // and nothing else, and its label always states the mode it is in. Choosing FULL or LOCAL
    // changes the persisted connection mode (which stops a live tunnel, because the transport
    // itself changes); SMART leaves the mode alone and lets the ranker pick the route.
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

    val homeSections = HomeStudio.visible(
        orderRaw = settings.homeLayout.sectionOrder,
        hiddenRaw = settings.homeLayout.hiddenSections,
        legacyStatsVisible = settings.modularShowStats,
        legacyShortcutsVisible = settings.modularShowShortcuts
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // MARBLE_EXPRESSIVE_PAGES_V251 — the shell owns the ground now.
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            top = 8.dp,
            bottom = MarbleTheme.bottomClearance
        ),
        verticalArrangement = Arrangement.spacedBy(MarbleSpec.SECTION_SPACING_DP.dp)
    ) {
        // MARBLE_HOME_STUDIO_V249 — Home draws the sections the user kept, in the order they put
        // them. Every section is one branch of one `when`, so there is exactly one place that decides
        // what a section is and one that decides whether it is here: the layout lives in
        // [HomeStudio], and this list only paints it.
        //
        // The two legacy switches (`modularShowStats`, `modularShowShortcuts`) are honoured as a
        // floor inside [HomeStudio.visible], so somebody who turned the speed card off two versions
        // ago still has it off, and the settings page keeps writing both records in one update.
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
                        MarbleIconButton(
                            glyph = MarbleGlyph.QR,
                            onClick = onOpenQrImport,
                            tint = colors.secondary
                        )
                        MarbleIconButton(
                            glyph = MarbleGlyph.PLUS,
                            onClick = onOpenAddSheet,
                            tint = colors.primary
                        )
                        MarbleIconButton(
                            glyph = MarbleGlyph.SHIELD,
                            onClick = onOpenDiagnostics,
                            tint = if (connected) colors.success else colors.onSurfaceVariant
                        )
                    }
                }

                HomeSection.CONNECT -> item(key = "home-hero") {
                    HomeHero(
                        connected = connected,
                        connecting = connecting,
                        disconnecting = disconnecting,
                        uptime = uptime,
                        activeRoute = activeRoute,
                        stateLabel = headlineFor(connected, connecting, disconnecting),
                        modeChip = modeChip,
                        onSelectMode = selectModeChip,
                        onToggle = toggleConnection,
                        onOpenServers = onOpenServers,
                        onOpenLogs = { showQuickLogs = true }
                    )
                }

                HomeSection.PRESETS -> item(key = "home-presets") {
                    SmartPresetStrip(
                        repo = repo,
                        onOpenAll = { onOpenSettingsPage(SettingsDestination.SMART_PRESETS) }
                    )
                }

                HomeSection.ROUTE -> item(key = "home-route-section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarbleSectionHeader(title = trx("Active Route"))
                        HomeRouteAndIdentityCard(
                            repo = repo,
                            activeRoute = activeRoute,
                            connected = connected,
                            onOpenServers = onOpenServers,
                            onOpenServerDetail = onOpenServerDetail,
                            onTriggerPing = triggerHomePing,
                            onOpenIpPage = onOpenIpPage
                        )
                    }
                }

                HomeSection.STATS -> item(key = "home-telemetry-section") {
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

                HomeSection.SHORTCUTS -> item(key = "home-shortcuts-section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarbleSectionHeader(title = trx("Quick Actions"))
                        // Council grid: 2x2 tiles — faster scan, less scroll, no duplicate list chrome
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                HomeQuickTile(
                                    title = strings.addFromClipboard,
                                    subtitle = trx("Paste a link"),
                                    glyph = MarbleGlyph.COPY,
                                    tint = colors.primary,
                                    onClick = pasteFromClipboard,
                                    modifier = Modifier.weight(1f)
                                )
                                HomeQuickTile(
                                    title = trx("Scan QR"),
                                    subtitle = trx("Scan to import"),
                                    glyph = MarbleGlyph.QR,
                                    tint = colors.secondary,
                                    onClick = onOpenQrImport,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                HomeQuickTile(
                                    title = trx("Smart Rank"),
                                    subtitle = trx("Find the fastest route"),
                                    glyph = MarbleGlyph.SPARK,
                                    tint = colors.warning,
                                    enabled = !repo.busy && !repo.probeActive,
                                    onClick = { repo.smartRank() },
                                    modifier = Modifier.weight(1f)
                                )
                                HomeQuickTile(
                                    title = trx("Censorship Lab"),
                                    subtitle = trx("Test the network"),
                                    glyph = MarbleGlyph.SHIELD,
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
    }

    // MARBLE_EXPRESSIVE_SHELL_V241 — the live core log, one tap from the status chip instead of
    // five taps into Settings → Diagnostics.
    if (showQuickLogs) {
        QuickLogsSheet(
            repo = repo,
            onDismiss = { showQuickLogs = false }
        )
    }
}

/**
 * MARBLE_EXPRESSIVE_SHELL_V241 — Home's whole decision, in two rows.
 *
 * Row one: the one pill. Row two: the mode chip (how to connect) and the status chip (why it
 * is not connected, plus the live core log one tap away). There is no second connect control,
 * no slide track and no deck of alternatives: the happy path is a single, physical button.
 *
 * MARBLE_WEIGHT_LADDER_V235 — the hierarchy survives the simplification: the pill's label is
 * the TITLE rung's emphasized twin (the one focal point of the screen), the chip labels stay on
 * LABEL and the status line on BODY. Nothing here invents a weight outside the ladder.
 */
@Composable
private fun HomeHero(
    connected: Boolean,
    connecting: Boolean,
    disconnecting: Boolean,
    uptime: String,
    activeRoute: ProxyProfile?,
    stateLabel: String,
    modeChip: ConnectModeChip,
    onSelectMode: (ConnectModeChip) -> Unit,
    onToggle: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val working = connecting || disconnecting

    // MARBLE_LIVE_TICKER_V249 — the clock keeps its own tempo; the state label changes only
    // when the tunnel changes state.
    val subline = when {
        connected -> "${trx(stateLabel)} · ${strings.uptime}"
        working -> trx(stateLabel)
        activeRoute != null -> cleanServerName(activeRoute.name)
        else -> strings.chooseRoute
    }
    val ticker = if (connected) faDigits(uptime) else ""

    // MARBLE_EXPRESSIVE_BENTO_V250 — Home's connection stage: one tonal canvas, one primary
    // action, and the mode/status controls beside it. The stage colour follows the tunnel.
    val stageColor by animateColorAsState(
        targetValue = when {
            connected -> colors.successContainer
            working -> colors.tertiaryContainer
            else -> colors.primaryContainer
        },
        animationSpec = marbleDefaultEffectsSpec(),
        label = "homeConnectionStage"
    )
    val onStage = when {
        connected -> colors.onSuccessContainer
        working -> colors.onTertiaryContainer
        else -> colors.onPrimaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(stageColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = trx("Connection"),
                        color = onStage.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    AnimatedContent(
                        targetState = stateLabel,
                        label = "homeConnectionState"
                    ) { label ->
                        Text(
                            text = trx(label),
                            color = onStage,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HomeStatusChip(
                    connected = connected,
                    working = working,
                    label = if (connected) strings.statusProtected else if (working) strings.securingRoute else strings.readyToConnect,
                    onClick = onOpenLogs
                )
            }

            MarbleConnectPill(
                connected = connected,
                connecting = connecting,
                disconnecting = disconnecting,
                title = if (connected) strings.disconnect else strings.connect,
                subtitle = subline,
                onToggle = onToggle,
                ticker = ticker
            )

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
                Spacer(Modifier.weight(1f))
                MarbleIconButton(
                    glyph = MarbleGlyph.ROUTE,
                    onClick = onOpenServers,
                    tint = onStage,
                    contentDescription = trx("Choose server")
                )
            }
        }
    }
}

/** The one-line state of the tunnel; tapping it opens the live core log. */
@Composable
private fun HomeStatusChip(
    connected: Boolean,
    working: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val health = when {
        connected -> ServerHealth.GOOD
        working -> ServerHealth.SLOW
        else -> ServerHealth.UNTESTED
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            MarblePetalMark(
                color = colors.primary.copy(alpha = 0.15f),
                size = 28.dp
            )
            MarbleHealthDot(health = health, size = 9.dp)
        }
        MarbleGlyphIcon(
            glyph = MarbleGlyph.TUNERS,
            color = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
            weight = 1.1f
        )
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
    subtitle: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    MarbleExpressiveTile(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        tint = tint,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        tag = "home-quick-$title"
    )
}

@Composable
private fun HomeRouteAndIdentityCard(
    repo: AppRepository,
    activeRoute: ProxyProfile?,
    connected: Boolean,
    onOpenServers: () -> Unit,
    onOpenServerDetail: (ProxyProfile) -> Unit,
    onTriggerPing: () -> Unit,
    onOpenIpPage: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
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

    val pingTone = when {
        isMeasuring -> colors.primary
        pingState == ConnectionPingState.MEASURED && pingMs in 1..180 -> colors.success
        pingState == ConnectionPingState.MEASURED && pingMs > 180 -> colors.warning
        pingState == ConnectionPingState.FAILED -> colors.error
        else -> colors.success
    }

    // MARBLE_IP_IDENTITY_V251 — Home still shows the exit at a glance, and the row now opens the
    // IP page instead of a sheet: the sheet was three facts, the page is every address this phone
    // holds plus whether each family answers, in every connection state.
    val ipReport = repo.ipStackReport
    val intel = repo.serverIntel
    val ipTitle = when {
        repo.ipStackScanning && ipReport?.exit?.discovered != true -> strings.resolving
        ipReport?.exit?.address?.isNotBlank() == true -> ipReport.exit!!.address
        intel != null && intel.ip.isNotBlank() -> intel.ip
        else -> strings.ipAddress
    }
    val ipSubtitle = when {
        ipReport?.exit?.discovered == true -> buildString {
            val detailed = com.marbleng.app.model.IpAddressBook.egressDetail(ipReport.exit)
            append(detailed.ifBlank { trx("Public exit address") })
            append(" · ")
            append(trx("Every address, in every state"))
        }
        intel != null -> listOf(intel.locationLabel, intel.datacenterLabel)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { trx("Tap to open the IP page") }
        repo.serverIntelError.isNotBlank() -> trx(repo.serverIntelError)
        else -> trx("Tap to open every address on this phone")
    }

    MarbleCard {
        MarbleListItem(
            title = activeRoute?.let { cleanServerName(it.name) } ?: strings.chooseRoute,
            subtitle = if (activeRoute != null) {
                "${protocolShortLabel(activeRoute)} · ${activeRoute.subscriptionName}"
            } else {
                trx("Select or import a server from the Servers tab")
            },
            leading = {
                MarbleServerAvatar(profile = activeRoute, countryCode = countryCode)
            },
            trailing = {
                MarbleBadge(
                    text = if (connected) strings.homeConnectedBadge else strings.changeRoute,
                    color = if (connected) colors.success else colors.primary
                )
            },
            onClick = onOpenServers
        )

        if (activeRoute != null) {
            MarbleDivider()
            MarbleListItem(
                title = trx("Inspect Server Configuration"),
                subtitle = "${activeRoute.host}:${activeRoute.port}",
                glyph = MarbleGlyph.TUNERS,
                iconTint = colors.secondary,
                onClick = { onOpenServerDetail(activeRoute) }
            )
        }

        MarbleDivider()
        MarbleListItem(
            title = "${strings.connectionPing}: $pingHeadline",
            subtitle = pingSub,
            glyph = MarbleGlyph.GAUGE,
            iconTint = pingTone,
            trailing = if (isMeasuring) {
                { MarblePulseDot(color = colors.primary) }
            } else null,
            onClick = onTriggerPing
        )

        MarbleDivider()
        MarbleListItem(
            title = ipTitle,
            subtitle = ipSubtitle,
            glyph = MarbleGlyph.GLOBE,
            iconTint = colors.tertiary,
            onClick = {
                if (ipReport == null && !repo.ipStackScanning) {
                    repo.refreshIpStack()
                }
                onOpenIpPage()
            }
        )
    }
}

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

    MarbleCard(contentPadding = PaddingValues(14.dp)) {
        // MARBLE_EXPRESSIVE_SHELL_V241 — the numbers alone were four dead strings. The graph
        // is the answer to "is anything actually moving?"; the tiles below stay because the
        // graph is a shape and the numbers are the truth.
        MarbleTrafficSparkline(
            downBps = downBps,
            upBps = upBps,
            active = live,
            height = MarbleSpec.SPARKLINE_HEIGHT_DP.dp
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStatTile(
                value = faDigits(formatRate(downBps)),
                label = strings.download,
                glyph = MarbleGlyph.DOWNLOAD,
                tint = colors.primary,
                prominent = true,
                modifier = Modifier.weight(1f)
            )
            HomeStatTile(
                value = faDigits(formatRate(upBps)),
                label = strings.upload,
                glyph = MarbleGlyph.UPLOAD,
                tint = colors.tertiary,
                prominent = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStatTile(
                value = faDigits(formatBytes(sessionBytes)),
                label = trx("Session"),
                glyph = MarbleGlyph.LAYERS,
                tint = colors.secondary,
                modifier = Modifier.weight(1f)
            )
            HomeStatTile(
                value = faDigits(formatBytes(totalBytes)),
                label = trx("Total"),
                glyph = MarbleGlyph.VAULT,
                tint = colors.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        if (showSocks) {
            Spacer(Modifier.height(6.dp))
            MarbleListItem(
                title = "${strings.socksProxyLabel} · 127.0.0.1:$socksPort",
                subtitle = trx("Tap to copy local SOCKS5 address"),
                glyph = MarbleGlyph.LINK,
                iconTint = colors.secondary,
                trailing = {
                    Text(
                        text = strings.copyAction,
                        color = colors.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                },
                showChevron = false,
                onClick = onCopySocks
            )
        }
    }
}

@Composable
private fun HomeStatTile(
    value: String,
    label: String,
    glyph: MarbleGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    if (prominent) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(24.dp))
                .background(tint.copy(alpha = 0.12f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarbleGlyphIcon(glyph = glyph, color = tint, modifier = Modifier.size(18.dp))
                Text(
                    text = label,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            MarbleRowIcon(
                glyph = glyph,
                tint = tint,
                size = 28.dp,
                glyphSize = 16.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
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
    }
}
