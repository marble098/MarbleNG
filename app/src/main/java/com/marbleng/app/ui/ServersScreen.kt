package com.marbleng.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.QrCode
import com.marbleng.app.core.ServersFilter
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.NodeSortMode
import com.marbleng.app.model.ProbeState
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.Subscription

private data class ServerGroupBucket(
    val id: String,
    val title: String,
    val subscription: Subscription?,
    val profiles: List<ProxyProfile>
)

@Composable
fun ServersScreen(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onOpenDetail: (ProxyProfile) -> Unit,
    onOpenAddSheet: () -> Unit,
    onOpenQrImport: () -> Unit,
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

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var renameTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var qrShareTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var editSubscription by remember { mutableStateOf<Subscription?>(null) }
    var editSubName by remember { mutableStateOf("") }
    var editSubUrl by remember { mutableStateOf("") }
    var deleteSubscription by remember { mutableStateOf<Subscription?>(null) }
    var collapsedGroups by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val benchmarks = repo.benchmarks.associateBy { it.profileId }
    val filter = ServersFilter(
        query = searchQuery,
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
        allProfiles.filter { filter.sourceId == "all" || it.subscriptionId == filter.sourceId }
    )

    val groups: List<ServerGroupBucket> = if (settings.serversGroupByCountry) {
        ServersQuery.groupByCountry(visibleProfiles).map { bucket ->
            ServerGroupBucket(
                id = "country:${bucket.country.code.ifBlank { "other" }}",
                title = bucket.country.name,
                subscription = null,
                profiles = bucket.profiles
            )
        }
    } else {
        buildList {
            repo.subscriptions.forEach { sub ->
                val nodes = visibleProfiles.filter { it.subscriptionId == sub.id }
                if (nodes.isNotEmpty() || (searchQuery.isBlank() && (filter.sourceId == "all" || filter.sourceId == sub.id))) {
                    add(ServerGroupBucket(id = sub.id, title = sub.name, subscription = sub, profiles = nodes))
                }
            }
            val manualNodes = visibleProfiles.filter { it.subscriptionId == "manual" }
            if (manualNodes.isNotEmpty() || filter.sourceId == "manual") {
                add(ServerGroupBucket(id = "manual", title = strings.homeManualGroup, subscription = null, profiles = manualNodes))
            }
        }
    }

    val selectOrConnect: (ProxyProfile) -> Unit = { profile ->
        if (repo.probeActive || repo.probeCancelling) {
            repo.setRuntimeMessage("Wait until ping finishes before changing server")
        } else if (repo.state == "CONNECTED" || repo.state == "CONNECTING") {
            onConnect(profile)
        } else {
            repo.selectProfile(profile)
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
        item(key = "servers-header") {
            IosLargeHeader(
                title = strings.serversTitle,
                subtitle = "${visibleProfiles.size} ${strings.serversUnit}"
            ) {
                IosIconActionButton(
                    glyph = if (repo.probeActive) MarbleGlyph.STOP else MarbleGlyph.GAUGE,
                    tint = if (repo.probeActive) colors.red else colors.green,
                    onClick = {
                        if (repo.probeActive || repo.probeCancelling) {
                            repo.cancelProbes()
                        } else {
                            repo.testSource(repo.librarySourceFilter)
                        }
                    }
                )
                IosIconActionButton(
                    glyph = MarbleGlyph.REFRESH,
                    tint = colors.indigo,
                    enabled = repo.subscriptions.isNotEmpty() && !repo.busy,
                    onClick = { repo.refreshAll() }
                )
                IosIconActionButton(
                    glyph = MarbleGlyph.FILTER,
                    tint = if (filter.isActive || settings.serversGroupByCountry) colors.orange else colors.subtitle,
                    onClick = { filterSheetOpen = true }
                )
                IosIconActionButton(
                    glyph = MarbleGlyph.PLUS,
                    tint = colors.accent,
                    onClick = onOpenAddSheet
                )
            }
        }

        item(key = "servers-search-and-chips") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IosSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = strings.searchServersHint,
                    trailingCount = "${visibleProfiles.size}"
                )

                IosChipStrip {
                    IosPillChip(
                        label = strings.homeAllGroups,
                        selected = repo.librarySourceFilter == "all",
                        onClick = { repo.selectLibrarySource("all") }
                    )
                    repo.subscriptions.forEach { sub ->
                        IosPillChip(
                            label = sub.name,
                            selected = repo.librarySourceFilter == sub.id,
                            onClick = { repo.selectLibrarySource(sub.id) }
                        )
                    }
                    IosPillChip(
                        label = strings.homeManualGroup,
                        selected = repo.librarySourceFilter == "manual",
                        onClick = { repo.selectLibrarySource("manual") }
                    )
                }

                if (protocolTallies.size > 1) {
                    IosChipStrip {
                        IosPillChip(
                            label = trx("All protocols"),
                            selected = settings.serversProtocolFilter.isBlank(),
                            accent = colors.indigo,
                            onClick = { repo.updateSettings(settings.copy(serversProtocolFilter = "")) }
                        )
                        protocolTallies.forEach { tally ->
                            IosPillChip(
                                label = "${tally.scheme} (${tally.count})",
                                selected = settings.serversProtocolFilter.equals(tally.scheme, ignoreCase = true),
                                accent = colors.indigo,
                                onClick = {
                                    val next = if (settings.serversProtocolFilter.equals(tally.scheme, ignoreCase = true)) {
                                        ""
                                    } else {
                                        tally.scheme
                                    }
                                    repo.updateSettings(settings.copy(serversProtocolFilter = next))
                                }
                            )
                        }
                    }
                }
            }
        }

        if (repo.probeActive || repo.probeCancelling) {
            item(key = "servers-probe-progress") {
                val total = repo.probeTotal.coerceAtLeast(1)
                val done = repo.probeDone.coerceIn(0, total)
                IosCard(contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IosSquircleIcon(glyph = MarbleGlyph.GAUGE, backgroundColor = colors.green)
                            Column {
                                Text(
                                    text = "${trx("Testing servers")} ($done / $total)",
                                    color = colors.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = repo.probeCurrentName.ifBlank { strings.measuring },
                                    color = colors.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IosButton(
                            label = strings.cancel,
                            onClick = { repo.cancelProbes() },
                            style = IosButtonStyle.DESTRUCTIVE,
                            compact = true
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total.toFloat() },
                        color = colors.green,
                        trackColor = colors.cardSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }

        if (allProfiles.isEmpty()) {
            item(key = "servers-empty") {
                IosCard(contentPadding = PaddingValues(24.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IosSquircleIcon(
                            glyph = MarbleGlyph.SERVER,
                            backgroundColor = colors.blue,
                            size = 52.dp,
                            glyphSize = 26.dp
                        )
                        Text(
                            text = strings.noServersTitle,
                            color = colors.title,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = strings.noServersBody,
                            color = colors.subtitle,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IosButton(
                                label = strings.pasteShortcut,
                                glyph = MarbleGlyph.COPY,
                                onClick = {
                                    val raw = clipboard.getText()?.text?.trim().orEmpty()
                                    if (raw.isBlank()) {
                                        repo.setRuntimeMessage(strings.clipboardNothingFound)
                                    } else {
                                        repo.importClipboard(raw, repo.intakeTargetOrManual(repo.librarySourceFilter))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            IosButton(
                                label = strings.qrShortcut,
                                glyph = MarbleGlyph.QR,
                                style = IosButtonStyle.TINTED,
                                onClick = onOpenQrImport,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IosButton(
                            label = trx("Add Server or Subscription"),
                            glyph = MarbleGlyph.PLUS,
                            style = IosButtonStyle.SECONDARY,
                            onClick = onOpenAddSheet,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else if (visibleProfiles.isEmpty()) {
            item(key = "servers-filtered-empty") {
                IosCard(contentPadding = PaddingValues(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = trx("No matching servers"),
                            color = colors.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = trx("Clear your search or filters to view all saved servers."),
                            color = colors.subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                        IosButton(
                            label = trx("Reset Filters"),
                            style = IosButtonStyle.TINTED,
                            onClick = {
                                searchQuery = ""
                                repo.selectLibrarySource("all")
                                repo.updateSettings(
                                    settings.copy(
                                        serversProtocolFilter = "",
                                        serversOnlyReachable = false,
                                        serversMaxPingMs = 0
                                    )
                                )
                            }
                        )
                    }
                }
            }
        } else {
            groups.forEach { bucket ->
                val isCollapsed = bucket.id in collapsedGroups
                item(key = "group-section-${bucket.id}") {
                    ServerGroupCard(
                        repo = repo,
                        bucket = bucket,
                        benchmarks = benchmarks,
                        collapsed = isCollapsed,
                        onToggleCollapse = {
                            collapsedGroups = if (isCollapsed) {
                                collapsedGroups - bucket.id
                            } else {
                                collapsedGroups + bucket.id
                            }
                        },
                        onSelectProfile = selectOrConnect,
                        onMoreProfile = { actionTarget = it },
                        onManageSubscription = { sub ->
                            editSubscription = sub
                            editSubName = sub.name
                            editSubUrl = sub.url
                        }
                    )
                }
            }
        }
    }

    if (filterSheetOpen) {
        ServersFilterSheet(
            repo = repo,
            onDismiss = { filterSheetOpen = false }
        )
    }

    actionTarget?.let { profile ->
        ServerQuickActionsSheet(
            repo = repo,
            profile = profile,
            onConnect = {
                actionTarget = null
                onConnect(profile)
            },
            onInspect = {
                actionTarget = null
                onOpenDetail(profile)
            },
            onShareQr = {
                actionTarget = null
                qrShareTarget = profile
            },
            onRename = {
                actionTarget = null
                renameDraft = cleanServerName(profile.name)
                renameTarget = profile
            },
            onMove = {
                actionTarget = null
                moveTarget = profile
            },
            onDelete = {
                actionTarget = null
                deleteTarget = profile
            },
            onDismiss = { actionTarget = null }
        )
    }

    renameTarget?.let { profile ->
        IosAlertDialog(
            title = trx("Rename Server"),
            onDismiss = { renameTarget = null },
            confirmLabel = trx("Save"),
            confirmEnabled = renameDraft.trim().isNotBlank(),
            onConfirm = {
                repo.renameProfile(profile.id, renameDraft.trim(), profile.subscriptionId)
                renameTarget = null
            }
        ) {
            IosTextField(
                label = trx("Display Name"),
                value = renameDraft,
                onValueChange = { renameDraft = it }
            )
        }
    }

    moveTarget?.let { profile ->
        val destinations = buildList {
            add("manual" to strings.homeManualGroup)
            repo.subscriptions.forEach { add(it.id to it.name) }
        }.filterNot { it.first == profile.subscriptionId }

        IosBottomSheet(
            title = trx("Move Server"),
            subtitle = cleanServerName(profile.name),
            onDismiss = { moveTarget = null }
        ) {
            if (destinations.isEmpty()) {
                Text(
                    text = trx("Add another subscription group first to move this server."),
                    color = colors.subtitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                IosCard {
                    destinations.forEachIndexed { index, (destId, destName) ->
                        if (index > 0) IosDivider()
                        IosListItem(
                            title = destName,
                            subtitle = trx("Move into this group"),
                            glyph = MarbleGlyph.FOLDER,
                            squircleColor = colors.blue,
                            onClick = {
                                repo.moveProfile(profile.id, profile.subscriptionId, destId)
                                moveTarget = null
                            }
                        )
                    }
                }
            }
        }
    }

    qrShareTarget?.let { profile ->
        ServerQrShareSheet(
            profile = profile,
            onCopy = { link ->
                clipboard.setText(AnnotatedString(link))
                repo.setRuntimeMessage("Config link copied")
            },
            onDismiss = { qrShareTarget = null }
        )
    }

    deleteTarget?.let { profile ->
        IosAlertDialog(
            title = trx("Delete Server?"),
            onDismiss = { deleteTarget = null },
            confirmLabel = trx("Delete"),
            destructive = true,
            onConfirm = {
                repo.removeProfile(profile.id, profile.subscriptionId)
                deleteTarget = null
            }
        ) {
            Text(
                text = "${cleanServerName(profile.name)} (${profile.subscriptionName})",
                color = colors.subtitle,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    editSubscription?.let { sub ->
        SubscriptionManageSheet(
            repo = repo,
            subscription = sub,
            nameDraft = editSubName,
            urlDraft = editSubUrl,
            onNameChange = { editSubName = it },
            onUrlChange = { editSubUrl = it },
            onSave = {
                if (repo.updateSubscription(sub.id, editSubName, editSubUrl)) {
                    editSubscription = null
                }
            },
            onRequestDelete = {
                editSubscription = null
                deleteSubscription = sub
            },
            onDismiss = { editSubscription = null }
        )
    }

    deleteSubscription?.let { sub ->
        IosAlertDialog(
            title = trx("Delete Subscription?"),
            onDismiss = { deleteSubscription = null },
            confirmLabel = trx("Delete"),
            destructive = true,
            onConfirm = {
                repo.removeSubscription(sub.id)
                deleteSubscription = null
            }
        ) {
            Text(
                text = "${sub.name} — ${trx("All servers managed by this subscription will be removed.")}",
                color = colors.subtitle,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ServerGroupCard(
    repo: AppRepository,
    bucket: ServerGroupBucket,
    benchmarks: Map<String, BenchmarkResult>,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onSelectProfile: (ProxyProfile) -> Unit,
    onMoreProfile: (ProxyProfile) -> Unit,
    onManageSubscription: (Subscription) -> Unit
) {
    val colors = IosTheme.colors
    val strings = Tr.now
    val sub = bucket.subscription
    val usedBytes = if (sub != null) sub.uploadBytes + sub.downloadBytes else 0L
    val quotaSubtitle = when {
        sub != null && sub.totalBytes > 0L ->
            "${bucket.profiles.size} ${strings.serversUnit} · ${formatBytes(usedBytes)} / ${formatBytes(sub.totalBytes)}"
        sub != null && sub.updatedAt > 0L ->
            "${bucket.profiles.size} ${strings.serversUnit} · ${trx(relativeTimeLabel(sub.updatedAt))}"
        else ->
            "${bucket.profiles.size} ${strings.serversUnit}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IosSectionHeader(
            title = bucket.title,
            badge = "${bucket.profiles.size}",
            badgeColor = if (sub != null) colors.indigo else colors.teal,
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sub != null && sub.url.isNotBlank()) {
                        Text(
                            text = trx("Refresh"),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.clickable(enabled = !repo.busy) {
                                repo.refresh(sub.id)
                            }
                        )
                    }
                    if (bucket.profiles.isNotEmpty() && !bucket.id.startsWith("country:")) {
                        Text(
                            text = strings.connectionPing,
                            color = colors.green,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.clickable(enabled = !repo.probeActive) {
                                repo.testSource(bucket.id)
                            }
                        )
                    }
                    if (sub != null) {
                        Text(
                            text = trx("Edit"),
                            color = colors.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable { onManageSubscription(sub) }
                        )
                    }
                }
            }
        )

        IosCard {
            IosListItem(
                title = bucket.title,
                subtitle = quotaSubtitle,
                glyph = if (sub != null) MarbleGlyph.LAYERS else MarbleGlyph.FOLDER,
                squircleColor = if (sub != null) colors.indigo else colors.teal,
                showChevron = false,
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.cardSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        MarbleGlyphIcon(
                            glyph = if (collapsed) MarbleGlyph.CHEVRON else MarbleGlyph.CHEVRON_DOWN,
                            color = colors.subtitle,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                },
                onClick = onToggleCollapse
            )

            if (!collapsed) {
                bucket.profiles.forEach { profile ->
                    val active = repo.isActiveProfile(profile)
                    val selected = repo.isSelectedProfile(profile)
                    val result = benchmarks[profile.id]
                    val probeState = repo.probeStateOf(profile.id)
                    val countryCode = repo.serverLocation(profile).code

                    IosDivider(insetStart = 66.dp)
                    ServerRowItem(
                        profile = profile,
                        countryCode = countryCode,
                        active = active,
                        selected = selected,
                        result = result,
                        probeState = probeState,
                        onClick = { onSelectProfile(profile) },
                        onMore = { onMoreProfile(profile) }
                    )
                }
                // Council: gentle footer hint for discoverability
                if (bucket.profiles.size > 6) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = trx("Tap server to select · ⋯ for actions"),
                            color = colors.tertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRowItem(
    profile: ProxyProfile,
    countryCode: String?,
    active: Boolean,
    selected: Boolean,
    result: BenchmarkResult?,
    probeState: ProbeState,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val colors = IosTheme.colors
    val pingText = trx(pingBadgeText(result, probeState))
    val pingTone = pingBadgeColor(result, probeState)
    val quietFailure = result != null && (result.success <= 0 || result.latencyMs <= 0.0)
    val subtitleText = buildString {
        append(protocolShortLabel(profile))
        if (profile.host.isNotBlank()) {
            append(" · ")
            append(profile.host)
        }
    }

    IosListItem(
        title = cleanServerName(profile.name),
        subtitle = subtitleText,
        titleColor = when {
            active -> colors.green
            selected -> colors.accent
            quietFailure -> colors.title.copy(alpha = 0.58f)
            else -> colors.title
        },
        leading = {
            IosServerAvatar(profile = profile, countryCode = countryCode)
        },
        showChevron = true,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (result != null || probeState != ProbeState.IDLE) {
                    if (quietFailure) {
                        // Council: quiet failure — muted, not alarming red
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(colors.tertiary.copy(alpha = 0.14f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = trx("No reply"),
                                color = colors.subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    } else {
                        IosBadge(text = pingText, color = pingTone)
                    }
                }
                if (active || selected) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.CHECK,
                        color = if (active) colors.green else colors.accent,
                        modifier = Modifier.size(16.dp),
                        weight = 1.2f
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.cardSecondary)
                        .clickable(role = Role.Button, onClick = onMore),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.MORE,
                        color = colors.subtitle,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun ServersFilterSheet(
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    val colors = IosTheme.colors
    val settings = repo.settings

    IosBottomSheet(
        title = trx("Sort & Filter"),
        subtitle = trx("Organize servers by latency, score, country or protocol"),
        onDismiss = onDismiss
    ) {
        IosSectionHeader(title = trx("Sort Order"))
        IosCard {
            val sortChoices = listOf(
                NodeSortMode.DEFAULT to trx("Subscription order"),
                NodeSortMode.PING to trx("Lowest ping"),
                NodeSortMode.SCORE to trx("Highest smart score"),
                NodeSortMode.NAME to trx("Name (A–Z)"),
                NodeSortMode.COUNTRY to trx("Country"),
                NodeSortMode.PROTOCOL to trx("Protocol")
            )
            sortChoices.forEachIndexed { idx, (mode, label) ->
                if (idx > 0) IosDivider()
                IosChoiceRow(
                    title = label,
                    selected = settings.nodeSortMode == mode,
                    glyph = MarbleGlyph.FILTER,
                    squircleColor = colors.blue,
                    onClick = { repo.updateSettings(settings.copy(nodeSortMode = mode)) }
                )
            }
        }

        IosSectionHeader(title = trx("Display Options"))
        IosCard {
            IosSwitchRow(
                title = trx("Reverse sort direction"),
                checked = settings.nodeSortReverse,
                glyph = MarbleGlyph.TUNERS,
                squircleColor = colors.indigo,
                onCheckedChange = { repo.updateSettings(settings.copy(nodeSortReverse = it)) }
            )
            IosDivider()
            IosSwitchRow(
                title = trx("Group by country"),
                subtitle = trx("Organize servers by exit country instead of subscription"),
                checked = settings.serversGroupByCountry,
                glyph = MarbleGlyph.GLOBE,
                squircleColor = colors.teal,
                onCheckedChange = { repo.updateSettings(settings.copy(serversGroupByCountry = it)) }
            )
            IosDivider()
            IosSwitchRow(
                title = trx("Only reachable servers"),
                subtitle = trx("Hide servers whose last test failed"),
                checked = settings.serversOnlyReachable,
                glyph = MarbleGlyph.CHECK,
                squircleColor = colors.green,
                onCheckedChange = { repo.updateSettings(settings.copy(serversOnlyReachable = it)) }
            )
        }
    }
}

@Composable
private fun ServerQuickActionsSheet(
    repo: AppRepository,
    profile: ProxyProfile,
    onConnect: () -> Unit,
    onInspect: () -> Unit,
    onShareQr: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = IosTheme.colors
    val clipboard = LocalClipboardManager.current

    IosBottomSheet(
        title = cleanServerName(profile.name),
        subtitle = "${protocolShortLabel(profile)} · ${profile.host}:${profile.port}",
        onDismiss = onDismiss
    ) {
        IosCard {
            IosListItem(
                title = Tr.now.connect,
                subtitle = trx("Connect immediately through this server"),
                glyph = MarbleGlyph.POWER,
                squircleColor = colors.green,
                onClick = onConnect
            )
            IosDivider()
            IosListItem(
                title = trx("Test Route Latency"),
                subtitle = trx("Run a full tunnel & delay test on this server"),
                glyph = MarbleGlyph.GAUGE,
                squircleColor = colors.blue,
                onClick = {
                    onDismiss()
                    repo.fullTest(profile)
                }
            )
            IosDivider()
            IosListItem(
                title = trx("Server Details & JSON"),
                subtitle = trx("Inspect security, IPv4/IPv6 reachability and outbound JSON"),
                glyph = MarbleGlyph.TUNERS,
                squircleColor = colors.indigo,
                onClick = onInspect
            )
            IosDivider()
            IosListItem(
                title = trx("Share QR Code"),
                subtitle = trx("Display scannable QR code or copy link"),
                glyph = MarbleGlyph.QR,
                squircleColor = colors.purple,
                onClick = onShareQr
            )
            IosDivider()
            IosListItem(
                title = trx("Copy Share Link / Config"),
                subtitle = trx("Copy URI or outbound JSON to clipboard"),
                glyph = MarbleGlyph.COPY,
                squircleColor = colors.teal,
                onClick = {
                    val payload = profile.raw.ifBlank { profile.configJson }
                    clipboard.setText(AnnotatedString(payload))
                    repo.setRuntimeMessage("Config copied")
                    onDismiss()
                }
            )
            IosDivider()
            IosListItem(
                title = trx("Rename"),
                glyph = MarbleGlyph.PENCIL,
                squircleColor = colors.orange,
                onClick = onRename
            )
            IosDivider()
            IosListItem(
                title = trx("Duplicate"),
                glyph = MarbleGlyph.LAYERS,
                squircleColor = colors.cyan,
                onClick = {
                    repo.duplicateProfile(profile.id, profile.subscriptionId)
                    onDismiss()
                }
            )
            IosDivider()
            IosListItem(
                title = trx("Move to Group"),
                glyph = MarbleGlyph.FOLDER,
                squircleColor = colors.blue,
                onClick = onMove
            )
            IosDivider()
            IosListItem(
                title = trx("Delete Server"),
                titleColor = colors.red,
                glyph = MarbleGlyph.TRASH,
                squircleColor = colors.red,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SubscriptionManageSheet(
    repo: AppRepository,
    subscription: Subscription,
    nameDraft: String,
    urlDraft: String,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = IosTheme.colors
    val clipboard = LocalClipboardManager.current
    val failedCount = repo.failedSubscriptionNodeCount(subscription.id, "TUNNEL")

    IosBottomSheet(
        title = subscription.name,
        subtitle = trx("Manage subscription URL, links and failed nodes"),
        onDismiss = onDismiss
    ) {
        IosTextField(
            label = trx("Subscription Name"),
            value = nameDraft,
            onValueChange = onNameChange
        )
        IosTextField(
            label = trx("HTTPS Subscription URL"),
            value = urlDraft,
            onValueChange = onUrlChange,
            placeholder = "https://..."
        )
        IosButton(
            label = trx("Save Changes"),
            onClick = onSave,
            enabled = nameDraft.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        IosCard {
            IosListItem(
                title = trx("Copy All Server Links"),
                subtitle = trx("Copy every server in this subscription to clipboard"),
                glyph = MarbleGlyph.COPY,
                squircleColor = colors.blue,
                onClick = {
                    val text = repo.subscriptionRawText(subscription.id)
                    if (text.isNotBlank()) {
                        clipboard.setText(AnnotatedString(text))
                        repo.setRuntimeMessage("Subscription links copied")
                    }
                    onDismiss()
                }
            )
            if (failedCount > 0) {
                IosDivider()
                IosListItem(
                    title = "${trx("Remove Failed Servers")} ($failedCount)",
                    subtitle = trx("Prune unreachable nodes from this subscription"),
                    glyph = MarbleGlyph.TRASH,
                    squircleColor = colors.orange,
                    onClick = {
                        repo.removeFailedSubscriptionNodes(subscription.id, "TUNNEL")
                        onDismiss()
                    }
                )
            }
            IosDivider()
            IosListItem(
                title = trx("Delete Subscription"),
                titleColor = colors.red,
                glyph = MarbleGlyph.TRASH,
                squircleColor = colors.red,
                onClick = onRequestDelete
            )
        }
    }
}

@Composable
internal fun ServerQrShareSheet(
    profile: ProxyProfile,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = IosTheme.colors
    val payload = profile.raw.ifBlank { profile.configJson }
    val qr = remember(payload) { runCatching { QrCode.encode(payload) }.getOrNull() }

    IosBottomSheet(
        title = cleanServerName(profile.name),
        subtitle = protocolShortLabel(profile),
        onDismiss = onDismiss
    ) {
        IosCard(contentPadding = PaddingValues(24.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (qr != null) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val edge = qr.size.coerceAtLeast(1)
                            val cell = size.minDimension / edge
                            for (r in 0 until edge) {
                                for (c in 0 until edge) {
                                    if (qr.isDark(r, c)) {
                                        drawRect(
                                            color = Color(0xFF1C1C1E),
                                            topLeft = Offset(c * cell, r * cell),
                                            size = Size(cell + 0.5f, cell + 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = trx("Config is too large for a single QR code — copy the text link below."),
                        color = colors.subtitle,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                IosButton(
                    label = trx("Copy Share Link"),
                    glyph = MarbleGlyph.COPY,
                    onClick = { onCopy(payload) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
