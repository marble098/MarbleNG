package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.model.DockSlotKind
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.dockSlotCaption
import com.marbleng.app.model.dockSlotDefaultCaption
import com.marbleng.app.model.parseDockSlotKind
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

data class ResolvedDockSlot(
    val kind: DockSlotKind,
    val title: String,
    val sourceId: String,
    val profiles: List<ProxyProfile>,
    val pinnedProfile: ProxyProfile?
)

fun resolveDockSlot(repo: AppRepository): ResolvedDockSlot {
    val settings = repo.settings
    val kind = parseDockSlotKind(settings.dockSlotKind)
    val customCaption = dockSlotCaption(settings.dockSlotLabel)
    return when (kind) {
        DockSlotKind.PULSE -> ResolvedDockSlot(
            kind = kind,
            title = customCaption.ifBlank { dockSlotDefaultCaption(kind, "") },
            sourceId = "all",
            profiles = emptyList(),
            pinnedProfile = null
        )

        DockSlotKind.SOURCE -> {
            val sourceId = settings.dockSlotSourceId.ifBlank { "all" }
            val subName = when (sourceId) {
                "all" -> "All Servers"
                "manual" -> "Manual"
                else -> repo.subscriptions.firstOrNull { it.id == sourceId }?.name ?: "Source"
            }
            val list = repo.libraryProfiles.filter { sourceId == "all" || it.subscriptionId == sourceId }
            ResolvedDockSlot(
                kind = kind,
                title = customCaption.ifBlank { dockSlotDefaultCaption(kind, subName) },
                sourceId = sourceId,
                profiles = list,
                pinnedProfile = null
            )
        }

        DockSlotKind.CONFIG -> {
            val pinned = repo.profile(settings.dockSlotProfileId, settings.dockSlotProfileSourceId)
                ?: repo.profile(settings.dockSlotProfileId)
                ?: repo.homeRoute()
            val name = pinned?.let { cleanServerName(it.name) }.orEmpty()
            ResolvedDockSlot(
                kind = kind,
                title = customCaption.ifBlank { dockSlotDefaultCaption(kind, name) },
                sourceId = pinned?.subscriptionId.orEmpty(),
                profiles = emptyList(),
                pinnedProfile = pinned
            )
        }
    }
}

@Composable
fun CustomSlotScreen(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onOpenDetail: (ProxyProfile) -> Unit,
    onCustomizeSlot: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val listState = rememberLazyListState()
    val slot = resolveDockSlot(repo)

    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    val benchmarks = repo.benchmarks.associateBy { it.profileId }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            top = 8.dp,
            bottom = MarbleTheme.bottomClearance
        ),
        verticalArrangement = Arrangement.spacedBy(MarbleSpec.SECTION_SPACING_DP.dp)
    ) {
        item(key = "slot-header") {
            MarbleLargeHeader(
                title = trx(slot.title),
                subtitle = when (slot.kind) {
                    DockSlotKind.PULSE -> trx("Live tunnel telemetry & quick diagnostics")
                    DockSlotKind.SOURCE -> "${slot.profiles.size} ${strings.serversUnit}"
                    DockSlotKind.CONFIG -> trx("One-tap pinned server shortcut")
                }
            ) {
                MarbleIconButton(
                    glyph = MarbleGlyph.TUNERS,
                    tint = colors.primary,
                    onClick = onCustomizeSlot
                )
            }
        }

        when (slot.kind) {
            DockSlotKind.PULSE -> {
                val connected = repo.state == "CONNECTED"
                val route = repo.homeRoute()

                item(key = "pulse-live") {
                    val uptime = rememberUptimeLabel(repo.connectedSinceMs)
                    // Council: pulse hero gets its own breathing card to explain the tab
                    MarbleCard(contentPadding = PaddingValues(18.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background((if (connected) colors.success else colors.primary).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                MarbleGlyphIcon(
                                    glyph = MarbleGlyph.SHIELD,
                                    color = if (connected) colors.success else colors.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                if (connected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(colors.success)
                                            .border(1.5.dp, colors.surfaceContainerLow, CircleShape)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (connected) strings.statusProtected else strings.readyToConnect,
                                    color = colors.onSurface,
                                    // MARBLE_WEIGHT_LADDER_V235 — the slot's status line is its
                                    // headline (700) over BODY prose (400), same as Home's hero.
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (connected) "${route?.let { cleanServerName(it.name) } ?: ""} · $uptime" else route?.let { cleanServerName(it.name) } ?: strings.chooseRoute,
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            MarbleButton(
                                label = if (connected) strings.disconnect else strings.connect,
                                glyph = if (connected) MarbleGlyph.STOP else MarbleGlyph.POWER,
                                style = if (connected) MarbleButtonStyle.DESTRUCTIVE else MarbleButtonStyle.PRIMARY,
                                onClick = {
                                    if (connected || repo.state == "CONNECTING") repo.stopVpn() else repo.reconnectLastOrAuto(onConnect)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // keep throughput + ping as compact secondary card
                    MarbleCard {
                        MarbleListItem(
                            title = "↓ ${faDigits(formatRate(repo.liveDownBps))}  ·  ↑ ${faDigits(formatRate(repo.liveUpBps))}",
                            subtitle = "${trx("Session")}: ${faDigits(formatBytes(repo.sessionBytes))} · ${trx("Total")}: ${faDigits(formatBytes(repo.totalUsageBytes))}",
                            glyph = MarbleGlyph.GAUGE,
                            iconTint = colors.secondary,
                            showChevron = false
                        )
                        MarbleDivider()
                        MarbleListItem(
                            title = if (repo.livePingMs > 0) faDigits("${repo.livePingMs} ms") else strings.testPing,
                            subtitle = if (repo.liveJitterMs > 0) "${trx("Jitter")}: ${faDigits("${repo.liveJitterMs} ms")}" else trx("Tap to measure active route latency"),
                            glyph = MarbleGlyph.RADAR,
                            iconTint = colors.tertiary,
                            trailing = if (repo.livePingMs > 0 && connected) { { MarblePulseDot(colors.success) } } else null,
                            onClick = { repo.measureHomePing() }
                        )
                    }
                }

                item(key = "pulse-tools") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarbleSectionHeader(title = trx("Tools"))
                        MarbleCard {
                            MarbleListItem(
                                title = trx("Smart Rank All Servers"),
                                subtitle = trx("Benchmark & rank your server library"),
                                glyph = MarbleGlyph.SPARK,
                                iconTint = colors.warning,
                                enabled = !repo.busy && !repo.probeActive,
                                onClick = { repo.smartRank() }
                            )
                            MarbleDivider()
                            MarbleListItem(
                                title = trx("Diagnostics, Logs & Privacy Audit"),
                                subtitle = trx("Open runtime logs, system doctor and leak checks"),
                                glyph = MarbleGlyph.INFO,
                                iconTint = colors.secondary,
                                onClick = onOpenDiagnostics
                            )
                            MarbleDivider()
                            MarbleListItem(
                                title = trx("Customize Fourth Tab"),
                                subtitle = trx("Pin a subscription, single server or live pulse"),
                                glyph = MarbleGlyph.DOCK,
                                iconTint = colors.primary,
                                onClick = onCustomizeSlot
                            )
                        }
                    }
                }
            }

            DockSlotKind.SOURCE -> {
                if (slot.profiles.isEmpty()) {
                    item(key = "slot-source-empty") {
                        MarbleEmptyState(
                            title = trx("No servers in this group"),
                            body = trx("Choose another subscription for the fourth tab or add servers."),
                            glyph = MarbleGlyph.LAYERS,
                            tint = colors.secondary
                        ) {
                            MarbleButton(
                                label = trx("Choose Group"),
                                onClick = onCustomizeSlot,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    val sorted = ServersQuery.sort(
                        profiles = slot.profiles,
                        mode = repo.settings.nodeSortMode,
                        reverse = repo.settings.nodeSortReverse,
                        benchmarks = benchmarks
                    )
                    item(key = "slot-source-list") {
                        MarbleCard {
                            sorted.forEachIndexed { idx, profile ->
                                if (idx > 0) MarbleDivider()
                                val active = repo.isActiveProfile(profile)
                                val selected = repo.isSelectedProfile(profile)
                                val result = benchmarks[profile.id]
                                val probeState = repo.probeStateOf(profile.id)
                                MarbleListItem(
                                    title = cleanServerName(profile.name),
                                    subtitle = "${protocolShortLabel(profile)} · ${profile.host}",
                                    titleColor = if (active) colors.success else if (selected) colors.primary else colors.onSurface,
                                    leading = {
                                        MarbleServerAvatar(
                                            profile = profile,
                                            countryCode = repo.serverLocation(profile).code
                                        )
                                    },
                                    trailing = {
                                        if (result != null) {
                                            MarbleBadge(
                                                text = trx(pingBadgeText(result, probeState)),
                                                color = pingBadgeColor(result, probeState)
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (repo.state == "CONNECTED" || repo.state == "CONNECTING") {
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
            }

            DockSlotKind.CONFIG -> {
                val pinned = slot.pinnedProfile
                item(key = "slot-config") {
                    if (pinned == null) {
                        MarbleEmptyState(
                            title = trx("No pinned server selected"),
                            body = trx("Pin a single server for one-tap connect in the fourth tab."),
                            glyph = MarbleGlyph.SERVER,
                            tint = colors.tertiary
                        ) {
                            MarbleButton(
                                label = trx("Pick a Server"),
                                onClick = onCustomizeSlot,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        val isActive = repo.isActiveProfile(pinned)
                        MarbleCard {
                            MarbleListItem(
                                title = cleanServerName(pinned.name),
                                subtitle = "${protocolShortLabel(pinned)} · ${pinned.host}:${pinned.port}",
                                leading = {
                                    MarbleServerAvatar(
                                        profile = pinned,
                                        countryCode = repo.serverLocation(pinned).code
                                    )
                                },
                                onClick = { onOpenDetail(pinned) }
                            )
                            MarbleDivider()
                            MarbleListItem(
                                title = if (isActive) strings.disconnect else strings.connect,
                                subtitle = if (isActive) {
                                    trx("Disconnect pinned server")
                                } else {
                                    trx("Connect immediately to this pinned server")
                                },
                                glyph = if (isActive) MarbleGlyph.STOP else MarbleGlyph.POWER,
                                iconTint = if (isActive) colors.error else colors.success,
                                onClick = {
                                    if (isActive) repo.stopVpn() else onConnect(pinned)
                                }
                            )
                            MarbleDivider()
                            MarbleListItem(
                                title = trx("Change Pinned Server"),
                                subtitle = trx("Select another server or customize tab appearance"),
                                glyph = MarbleGlyph.DOCK,
                                iconTint = colors.primary,
                                onClick = onCustomizeSlot
                            )
                        }
                    }
                }
            }
        }
    }
}
