package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.model.DockSlotKind
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.dockSlotCaption
import com.marbleng.app.model.dockSlotDefaultCaption
import com.marbleng.app.model.parseDockSlotKind

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
    val colors = IosTheme.colors
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
        item(key = "slot-header") {
            IosLargeHeader(
                title = trx(slot.title),
                subtitle = when (slot.kind) {
                    DockSlotKind.PULSE -> trx("Live tunnel telemetry & quick diagnostics")
                    DockSlotKind.SOURCE -> "${slot.profiles.size} ${strings.serversUnit}"
                    DockSlotKind.CONFIG -> trx("One-tap pinned server shortcut")
                }
            ) {
                IosIconActionButton(
                    glyph = MarbleGlyph.TUNERS,
                    tint = colors.accent,
                    onClick = onCustomizeSlot
                )
            }
        }

        when (slot.kind) {
            DockSlotKind.PULSE -> {
                val connected = repo.state == "CONNECTED"
                val uptime = rememberUptimeLabel(repo.connectedSinceMs)
                val route = repo.homeRoute()

                item(key = "pulse-live") {
                    IosCard {
                        IosListItem(
                            title = if (connected) strings.statusProtected else strings.readyToConnect,
                            subtitle = if (connected) {
                                "${route?.let { cleanServerName(it.name) } ?: ""} · $uptime"
                            } else {
                                route?.let { cleanServerName(it.name) } ?: strings.chooseRoute
                            },
                            glyph = MarbleGlyph.SHIELD,
                            squircleColor = if (connected) colors.green else colors.blue,
                            onClick = {
                                if (connected || repo.state == "CONNECTING") {
                                    repo.stopVpn()
                                } else {
                                    repo.reconnectLastOrAuto(onConnect)
                                }
                            }
                        )
                        IosDivider()
                        IosListItem(
                            title = "↓ ${formatRate(repo.liveDownBps)}  ·  ↑ ${formatRate(repo.liveUpBps)}",
                            subtitle = "${trx("Session")}: ${formatBytes(repo.sessionBytes)} · ${trx("Total")}: ${formatBytes(repo.totalUsageBytes)}",
                            glyph = MarbleGlyph.GAUGE,
                            squircleColor = colors.indigo,
                            showChevron = false
                        )
                        IosDivider()
                        IosListItem(
                            title = if (repo.livePingMs > 0) "${repo.livePingMs} ms" else strings.testPing,
                            subtitle = if (repo.liveJitterMs > 0) {
                                "${trx("Jitter")}: ${repo.liveJitterMs} ms"
                            } else {
                                trx("Tap to measure active route latency")
                            },
                            glyph = MarbleGlyph.RADAR,
                            squircleColor = colors.teal,
                            onClick = { repo.measureHomePing() }
                        )
                    }
                }

                item(key = "pulse-tools") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IosSectionHeader(title = trx("Tools"))
                        IosCard {
                            IosListItem(
                                title = trx("Smart Rank All Servers"),
                                subtitle = trx("Benchmark & rank your server library"),
                                glyph = MarbleGlyph.SPARK,
                                squircleColor = colors.orange,
                                enabled = !repo.busy && !repo.probeActive,
                                onClick = { repo.smartRank() }
                            )
                            IosDivider()
                            IosListItem(
                                title = trx("Diagnostics, Logs & Privacy Audit"),
                                subtitle = trx("Open runtime logs, system doctor and leak checks"),
                                glyph = MarbleGlyph.INFO,
                                squircleColor = colors.purple,
                                onClick = onOpenDiagnostics
                            )
                            IosDivider()
                            IosListItem(
                                title = trx("Customize Fourth Tab"),
                                subtitle = trx("Pin a subscription, single server or live pulse"),
                                glyph = MarbleGlyph.DOCK,
                                squircleColor = colors.blue,
                                onClick = onCustomizeSlot
                            )
                        }
                    }
                }
            }

            DockSlotKind.SOURCE -> {
                if (slot.profiles.isEmpty()) {
                    item(key = "slot-source-empty") {
                        IosCard(contentPadding = PaddingValues(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = trx("No servers in this group"),
                                    color = colors.title,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = trx("Choose another subscription for the fourth tab or add servers."),
                                    color = colors.subtitle,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IosButton(
                                    label = trx("Choose Group"),
                                    onClick = onCustomizeSlot,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
                        IosCard {
                            sorted.forEachIndexed { idx, profile ->
                                if (idx > 0) IosDivider()
                                val active = repo.isActiveProfile(profile)
                                val selected = repo.isSelectedProfile(profile)
                                val result = benchmarks[profile.id]
                                val probeState = repo.probeStateOf(profile.id)
                                IosListItem(
                                    title = cleanServerName(profile.name),
                                    subtitle = "${protocolShortLabel(profile)} · ${profile.host}",
                                    titleColor = if (active) colors.green else if (selected) colors.accent else colors.title,
                                    leading = {
                                        IosServerAvatar(
                                            profile = profile,
                                            countryCode = repo.serverLocation(profile).code
                                        )
                                    },
                                    trailing = {
                                        if (result != null) {
                                            IosBadge(
                                                text = pingBadgeText(result, probeState),
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
                        IosCard(contentPadding = PaddingValues(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = trx("No pinned server selected"),
                                    color = colors.title,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                IosButton(
                                    label = trx("Pick a Server"),
                                    onClick = onCustomizeSlot,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        val isActive = repo.isActiveProfile(pinned)
                        IosCard {
                            IosListItem(
                                title = cleanServerName(pinned.name),
                                subtitle = "${protocolShortLabel(pinned)} · ${pinned.host}:${pinned.port}",
                                leading = {
                                    IosServerAvatar(
                                        profile = pinned,
                                        countryCode = repo.serverLocation(pinned).code
                                    )
                                },
                                onClick = { onOpenDetail(pinned) }
                            )
                            IosDivider()
                            IosListItem(
                                title = if (isActive) strings.disconnect else strings.connect,
                                subtitle = if (isActive) {
                                    trx("Disconnect pinned server")
                                } else {
                                    trx("Connect immediately to this pinned server")
                                },
                                glyph = if (isActive) MarbleGlyph.STOP else MarbleGlyph.POWER,
                                squircleColor = if (isActive) colors.red else colors.green,
                                onClick = {
                                    if (isActive) repo.stopVpn() else onConnect(pinned)
                                }
                            )
                            IosDivider()
                            IosListItem(
                                title = trx("Change Pinned Server"),
                                subtitle = trx("Select another server or customize tab appearance"),
                                glyph = MarbleGlyph.DOCK,
                                squircleColor = colors.blue,
                                onClick = onCustomizeSlot
                            )
                        }
                    }
                }
            }
        }
    }
}
