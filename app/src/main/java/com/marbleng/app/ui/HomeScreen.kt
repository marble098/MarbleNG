package com.marbleng.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.coreEngine
import com.marbleng.app.model.ConnectButtonStyle
import com.marbleng.app.model.ConnectDeckAction
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.HomePingAction
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.SlideConnectSpec
import com.marbleng.app.model.parseConnectButtonStyle
import com.marbleng.app.model.parseHomePingAction

@Composable
fun HomeScreen(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onOpenServers: () -> Unit,
    onOpenServerDetail: (ProxyProfile) -> Unit,
    onOpenQrImport: () -> Unit,
    onOpenAddSheet: () -> Unit,
    onOpenExitIpSheet: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettingsPage: (SettingsDestination) -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val colors = IosTheme.colors
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

    // MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — the three deck buttons own independent lights.
    // Only the button that actually lit the current route stays on; disconnecting clears it.
    var litDeckId by rememberSaveable { mutableStateOf("") }
    val litDeckAction = if (litDeckId.isBlank()) {
        null
    } else {
        ConnectDeckAction.fromId(litDeckId)
    }

    LaunchedEffect(repo.state) {
        if (repo.state == "DISCONNECTED" || repo.state == "BLOCKED") {
            litDeckId = ""
        }
    }

    val activateDeck: (ConnectDeckAction) -> Unit = { action ->
        if (!disconnecting) {
            when {
                (connected || connecting) && litDeckId == action.id -> {
                    litDeckId = ""
                    repo.stopVpn()
                }
                else -> {
                    litDeckId = action.id
                    when (action) {
                        ConnectDeckAction.QUICK -> repo.reconnectLastOrAuto(onConnect)
                        ConnectDeckAction.SMART -> {
                            val winner = repo.runAutoServerSelection(reason = "connect-deck")?.profile
                            if (winner != null) {
                                onConnect(winner)
                            } else {
                                litDeckId = ""
                                repo.setRuntimeMessage(
                                    "No route to recommend yet — measure a server first"
                                )
                            }
                        }
                        ConnectDeckAction.LIGHT -> {
                            repo.setConnectionMode(ConnectionMode.LOCAL_PROXY)
                            repo.auto(onConnect)
                        }
                    }
                }
            }
        }
    }

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

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = IosSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            end = IosSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            top = 8.dp,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(IosSpec.SECTION_SPACING_DP.dp)
    ) {
        item(key = "home-header") {
            IosLargeHeader(
                title = "MarbleNG",
                subtitle = headerSubtitle
            ) {
                IosIconActionButton(
                    glyph = MarbleGlyph.QR,
                    onClick = onOpenQrImport,
                    tint = colors.indigo
                )
                IosIconActionButton(
                    glyph = MarbleGlyph.PLUS,
                    onClick = onOpenAddSheet,
                    tint = colors.accent
                )
                IosIconActionButton(
                    glyph = MarbleGlyph.SHIELD,
                    onClick = onOpenDiagnostics,
                    tint = if (connected) colors.green else colors.subtitle
                )
            }
        }

        item(key = "home-hero-card") {
            HomeConnectionHeroCard(
                connected = connected,
                connecting = connecting,
                disconnecting = disconnecting,
                uptime = uptime,
                activeRoute = activeRoute,
                sliderMode = parseConnectButtonStyle(settings.connectButtonStyle) == ConnectButtonStyle.SLIDE,
                onToggle = toggleConnection
            )
        }

        item(key = "home-connect-deck") {
            ConnectDeckSection(
                litAction = litDeckAction,
                connected = connected,
                connecting = connecting,
                disconnecting = disconnecting,
                onActivate = activateDeck
            )
        }

        item(key = "home-route-section") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Active Route"))
                HomeRouteAndIdentityCard(
                    repo = repo,
                    activeRoute = activeRoute,
                    connected = connected,
                    onOpenServers = onOpenServers,
                    onOpenServerDetail = onOpenServerDetail,
                    onTriggerPing = triggerHomePing,
                    onOpenExitIpSheet = onOpenExitIpSheet
                )
            }
        }

        if (settings.modularShowStats) {
            item(key = "home-telemetry-section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosSectionHeader(title = strings.networkSpeed)
                    HomeTelemetryCard(
                        downBps = repo.liveDownBps,
                        upBps = repo.liveUpBps,
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

        if (settings.modularShowShortcuts) {
            item(key = "home-shortcuts-section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosSectionHeader(title = trx("Quick Actions"))
                    // Council grid: 2x2 tiles — faster scan, less scroll, no duplicate list chrome
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            HomeQuickTile(
                                title = strings.addFromClipboard,
                                glyph = MarbleGlyph.COPY,
                                tint = colors.blue,
                                onClick = pasteFromClipboard,
                                modifier = Modifier.weight(1f)
                            )
                            HomeQuickTile(
                                title = trx("Scan QR"),
                                glyph = MarbleGlyph.QR,
                                tint = colors.indigo,
                                onClick = onOpenQrImport,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            HomeQuickTile(
                                title = trx("Smart Rank"),
                                glyph = MarbleGlyph.SPARK,
                                tint = colors.orange,
                                enabled = !repo.busy && !repo.probeActive,
                                onClick = { repo.smartRank() },
                                modifier = Modifier.weight(1f)
                            )
                            HomeQuickTile(
                                title = trx("Censorship Lab"),
                                glyph = MarbleGlyph.SHIELD,
                                tint = colors.green,
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

@Composable
private fun HomeConnectionHeroCard(
    connected: Boolean,
    connecting: Boolean,
    disconnecting: Boolean,
    uptime: String,
    activeRoute: ProxyProfile?,
    sliderMode: Boolean,
    onToggle: () -> Unit
) {
    val colors = IosTheme.colors
    val strings = Tr.now

    val stateTone by animateColorAsState(
        targetValue = when {
            connected -> colors.green
            connecting || disconnecting -> colors.orange
            else -> colors.accent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "heroTone"
    )

    val ringScale by animateFloatAsState(
        targetValue = if (connected) 1.06f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.62f),
        label = "heroRingScale"
    )

    val headline = when {
        connected -> strings.statusProtected
        connecting -> strings.securingRoute
        disconnecting -> strings.disconnecting
        else -> strings.readyToConnect
    }

    val subline = when {
        connected -> "${strings.uptime} · $uptime"
        connecting -> strings.securingRoute
        disconnecting -> strings.closingRoute
        activeRoute != null -> cleanServerName(activeRoute.name)
        else -> strings.chooseRoute
    }

    val actionLabel = when {
        connected || connecting -> strings.disconnect
        else -> strings.connect
    }

    IosCard(contentPadding = PaddingValues(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Council: breathing outer halo when protected — signals liveness without animation spam
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(stateTone.copy(alpha = if (connected) 0.14f else 0.10f))
                    .border(1.dp, stateTone.copy(alpha = if (connected) 0.18f else 0.0f), CircleShape)
                    .clickable(
                        enabled = !disconnecting,
                        role = Role.Button,
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(stateTone)
                        .border(0.75.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = if (connected) MarbleGlyph.SHIELD else MarbleGlyph.POWER,
                        color = Color.White,
                        modifier = Modifier.size(32.dp),
                        weight = 1.18f
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = headline,
                    color = colors.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subline,
                    color = if (connected) stateTone else colors.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (connected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (!connected && activeRoute == null) {
                    Text(
                        text = trx("Import a server to begin — paste, QR or file"),
                        color = colors.tertiary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (sliderMode) {
                IosSlideConnectBar(
                    connected = connected || connecting,
                    enabled = !disconnecting,
                    tone = stateTone,
                    label = if (connected || connecting) strings.slideToDisconnect else strings.slideToConnect,
                    onTriggered = onToggle
                )
            } else {
                IosButton(
                    label = actionLabel,
                    onClick = onToggle,
                    style = IosButtonStyle.PRIMARY,
                    colorOverride = if (connected || connecting) colors.red else stateTone,
                    glyph = if (connected || connecting) MarbleGlyph.STOP else MarbleGlyph.POWER,
                    enabled = !disconnecting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HomeQuickTile(
    title: String,
    glyph: MarbleGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = IosTheme.colors
    Column(
        modifier = modifier
            .clip(IosCardShape)
            .background(colors.card)
            .border(0.85.dp, colors.cardBorder, IosCardShape)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(IosSquircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            MarbleGlyphIcon(glyph = glyph, color = tint, modifier = Modifier.size(18.dp), weight = 1.1f)
        }
        Text(
            text = title,
            color = colors.title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IosSlideConnectBar(
    connected: Boolean,
    enabled: Boolean,
    tone: Color,
    label: String,
    onTriggered: () -> Unit
) {
    val colors = IosTheme.colors
    val density = LocalDensity.current
    // MARBLE_RTL_CONNECT_CONTROL_V234 — the slide control mirrors with the language. `IntOffset`
    // is a pixel offset: on a Persian phone the thumb rests on the right edge and may only travel
    // left, the drag delta is negative, and both chevrons point the other way. All three signs live
    // in `SlideConnectSpec`, which the host JVM test pins.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragFraction by remember(connected) { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(IosSpec.SLIDE_TRACK_HEIGHT_DP.dp)
            .clip(CircleShape)
            .background(colors.cardSecondary)
            .border(0.85.dp, colors.divider, CircleShape)
            .padding(4.dp)
    ) {
        val thumbSize = IosSpec.SLIDE_THUMB_SIZE_DP.dp
        val travelPx = with(density) { (maxWidth - thumbSize).toPx().coerceAtLeast(1f) }
        val offsetPx = SlideConnectSpec.thumbOffsetPx(travelPx, dragFraction, rtl)
        val fillFraction = dragFraction.coerceIn(0f, 1f)

        // Energy fill — grows under thumb, brighter at head
        if (fillFraction > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction = ((fillFraction * travelPx + with(density) { thumbSize.toPx() }) / with(density) { maxWidth.toPx() }).coerceIn(0f, 1f))
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.16f))
            )
        }

        // Guide chevrons fading as thumb approaches
        if (!connected && dragFraction < 0.6f) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .alpha((1f - dragFraction * 1.8f).coerceIn(0f, 0.55f)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    MarbleGlyphIcon(
                        glyph = if (rtl) MarbleGlyph.CHEVRON_LEFT else MarbleGlyph.CHEVRON,
                        color = colors.subtitle.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Text(
            text = label,
            color = colors.subtitle,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetPx, 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (connected) colors.red else tone)
                .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                .pointerInput(connected, enabled, travelPx, rtl) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragFraction = SlideConnectSpec.dragFraction(
                                current = dragFraction,
                                dragAmountPx = dragAmount,
                                travelPx = travelPx,
                                rtl = rtl
                            )
                        },
                        onDragEnd = {
                            if (SlideConnectSpec.released(dragFraction)) {
                                dragFraction = 0f
                                onTriggered()
                            } else {
                                dragFraction = 0f
                            }
                        },
                        onDragCancel = { dragFraction = 0f }
                    )
                }
                .clickable(enabled = enabled, onClick = onTriggered),
            contentAlignment = Alignment.Center
        ) {
            MarbleGlyphIcon(
                glyph = when {
                    connected -> MarbleGlyph.STOP
                    rtl -> MarbleGlyph.CHEVRON_LEFT
                    else -> MarbleGlyph.CHEVRON
                },
                color = Color.White,
                modifier = Modifier.size(20.dp),
                weight = 1.15f
            )
        }
    }
}

@Composable
private fun HomeRouteAndIdentityCard(
    repo: AppRepository,
    activeRoute: ProxyProfile?,
    connected: Boolean,
    onOpenServers: () -> Unit,
    onOpenServerDetail: (ProxyProfile) -> Unit,
    onTriggerPing: () -> Unit,
    onOpenExitIpSheet: () -> Unit
) {
    val colors = IosTheme.colors
    val strings = Tr.now
    val countryCode = activeRoute?.let { repo.serverLocation(it).code }

    val pingState = if (connected) repo.connectionPingState else repo.selectedPingState
    val pingMs = if (connected) repo.connectionPingMs else repo.selectedPingMs
    val pingFailure = if (connected) repo.connectionPingFailure else repo.selectedPingFailure
    val isMeasuring = pingState == ConnectionPingState.MEASURING || repo.probeActive

    val pingHeadline = when {
        isMeasuring -> strings.measuring
        pingState == ConnectionPingState.MEASURED && pingMs > 0 -> "$pingMs ms"
        pingState == ConnectionPingState.FAILED -> pingFailure.ifBlank { strings.pingFailed }
        repo.livePingMs > 0 && connected -> "${repo.livePingMs} ms"
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
            append(" ${repo.liveJitterMs} ms")
        }
        if (repo.probeActive) {
            append(" · ")
            append(trx("Tap to stop"))
        }
    }

    val pingTone = when {
        isMeasuring -> colors.blue
        pingState == ConnectionPingState.MEASURED && pingMs in 1..180 -> colors.green
        pingState == ConnectionPingState.MEASURED && pingMs > 180 -> colors.orange
        pingState == ConnectionPingState.FAILED -> colors.red
        else -> colors.green
    }

    val intel = repo.serverIntel
    val ipTitle = when {
        repo.serverIntelLoading -> strings.resolving
        intel != null && intel.ip.isNotBlank() -> intel.ip
        else -> strings.ipAddress
    }
    val ipSubtitle = when {
        intel != null -> listOf(intel.locationLabel, intel.datacenterLabel)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { trx("Tap to inspect exit IP details") }
        repo.serverIntelError.isNotBlank() -> trx(repo.serverIntelError)
        else -> trx("Tap to inspect exit IP, country and ASN")
    }

    IosCard {
        IosListItem(
            title = activeRoute?.let { cleanServerName(it.name) } ?: strings.chooseRoute,
            subtitle = if (activeRoute != null) {
                "${protocolShortLabel(activeRoute)} · ${activeRoute.subscriptionName}"
            } else {
                trx("Select or import a server from the Servers tab")
            },
            leading = {
                IosServerAvatar(profile = activeRoute, countryCode = countryCode)
            },
            trailing = {
                IosBadge(
                    text = if (connected) strings.homeConnectedBadge else strings.changeRoute,
                    color = if (connected) colors.green else colors.accent
                )
            },
            onClick = onOpenServers
        )

        if (activeRoute != null) {
            IosDivider()
            IosListItem(
                title = trx("Inspect Server Configuration"),
                subtitle = "${activeRoute.host}:${activeRoute.port}",
                glyph = MarbleGlyph.TUNERS,
                squircleColor = colors.indigo,
                onClick = { onOpenServerDetail(activeRoute) }
            )
        }

        IosDivider()
        IosListItem(
            title = "${strings.connectionPing}: $pingHeadline",
            subtitle = pingSub,
            glyph = MarbleGlyph.GAUGE,
            squircleColor = pingTone,
            trailing = if (isMeasuring) {
                { IosPulseDot(color = colors.blue) }
            } else null,
            onClick = onTriggerPing
        )

        IosDivider()
        IosListItem(
            title = ipTitle,
            subtitle = ipSubtitle,
            glyph = MarbleGlyph.GLOBE,
            squircleColor = colors.teal,
            onClick = {
                if (intel == null && !repo.serverIntelLoading) {
                    repo.refreshServerIntel(force = true)
                }
                onOpenExitIpSheet()
            }
        )
    }
}

@Composable
private fun HomeTelemetryCard(
    downBps: Long,
    upBps: Long,
    sessionBytes: Long,
    totalBytes: Long,
    showSocks: Boolean,
    socksPort: Int,
    onCopySocks: () -> Unit
) {
    val colors = IosTheme.colors
    val strings = Tr.now

    IosCard {
        // Council: split down/up into scannable pair, session/total into second pair
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStatTile(
                value = formatRate(downBps),
                label = strings.download,
                glyph = MarbleGlyph.DOWNLOAD,
                tint = colors.blue,
                modifier = Modifier.weight(1f)
            )
            HomeStatTile(
                value = formatRate(upBps),
                label = strings.upload,
                glyph = MarbleGlyph.UPLOAD,
                tint = colors.teal,
                modifier = Modifier.weight(1f)
            )
        }
        IosDivider(insetStart = 16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStatTile(
                value = formatBytes(sessionBytes),
                label = trx("Session"),
                glyph = MarbleGlyph.LAYERS,
                tint = colors.purple,
                modifier = Modifier.weight(1f)
            )
            HomeStatTile(
                value = formatBytes(totalBytes),
                label = trx("Total"),
                glyph = MarbleGlyph.VAULT,
                tint = colors.indigo,
                modifier = Modifier.weight(1f)
            )
        }
        if (showSocks) {
            IosDivider()
            IosListItem(
                title = "${strings.socksProxyLabel} · 127.0.0.1:$socksPort",
                subtitle = trx("Tap to copy local SOCKS5 address"),
                glyph = MarbleGlyph.LINK,
                squircleColor = colors.indigo,
                trailing = {
                    Text(
                        text = strings.copyAction,
                        color = colors.accent,
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
    modifier: Modifier = Modifier
) {
    val colors = IosTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardSecondary)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(IosSquircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            MarbleGlyphIcon(glyph = glyph, color = tint, modifier = Modifier.size(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                color = colors.title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                color = colors.subtitle,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
