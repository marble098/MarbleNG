package com.marbleng.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.marbleng.app.model.ServerHealth
import com.marbleng.app.model.Subscription
import com.marbleng.app.model.serverHealthOf
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.onSuccess
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.successContainer
import com.marbleng.app.ui.theme.warning

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
    val colors = MaterialTheme.colorScheme
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
    var fabExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    val pasteFromClipboard: () -> Unit = {
        val raw = clipboard.getText()?.text?.trim().orEmpty()
        if (raw.isBlank()) {
            repo.setRuntimeMessage(strings.clipboardNothingFound)
        } else {
            repo.importClipboard(raw, repo.intakeTargetOrManual(repo.librarySourceFilter))
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = repo.busy,
            onRefresh = {
                if (repo.subscriptions.isNotEmpty() && !repo.busy) {
                    repo.refreshAll()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .statusBarsPadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    top = 8.dp,
                    bottom = MarbleTheme.bottomClearance
                ),
                verticalArrangement = Arrangement.spacedBy(MarbleSpec.SECTION_SPACING_DP.dp)
            ) {

        item(key = "servers-header") {
            MarbleLargeHeader(
                title = strings.serversTitle,
                subtitle = "${faDigits("${visibleProfiles.size}")} ${strings.serversUnit}",
                collapseFraction = collapseFractionOf(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
                )
            ) {
                MarbleIconButton(
                    glyph = if (repo.probeActive) MarbleGlyph.STOP else MarbleGlyph.GAUGE,
                    tint = if (repo.probeActive) colors.error else colors.success,
                    onClick = {
                        if (repo.probeActive || repo.probeCancelling) {
                            repo.cancelProbes()
                        } else {
                            repo.testSource(repo.librarySourceFilter)
                        }
                    }
                )
                MarbleIconButton(
                    glyph = MarbleGlyph.REFRESH,
                    tint = colors.secondary,
                    enabled = repo.subscriptions.isNotEmpty() && !repo.busy,
                    onClick = { repo.refreshAll() }
                )
                MarbleIconButton(
                    glyph = MarbleGlyph.FILTER,
                    tint = if (filter.isActive || settings.serversGroupByCountry) colors.warning else colors.onSurfaceVariant,
                    onClick = { filterSheetOpen = true }
                )
            }
        }

        item(key = "servers-search-and-chips") {
            // MARBLE_M3_EXPRESSIVE_V242 — the 5-layer header collapses on scroll: the search
            // stays pinned while the sort / group / protocol strips fold away with a spring,
            // so half the viewport is not chrome before the first server appears.
            val chipsCollapsed by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MarbleSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = strings.searchServersHint,
                    trailingCount = faDigits("${visibleProfiles.size}")
                )

                AnimatedVisibility(
                    visible = !chipsCollapsed,
                    enter = slideInVertically(
                        initialOffsetY = { -it / 3 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it / 3 },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // MARBLE_EXPRESSIVE_SHELL_V241 — sorting is a first-class question on this page
                        // ("who is fastest?"), so it is a chip row, not a buried filter-sheet control.
                        MarbleChipStrip {
                            MarblePillChip(
                                label = trx("Lowest ping"),
                                selected = settings.nodeSortMode == NodeSortMode.PING && !settings.nodeSortReverse,
                                accent = colors.success,
                                onClick = {
                                    repo.updateSettings(
                                        settings.copy(nodeSortMode = NodeSortMode.PING, nodeSortReverse = false)
                                    )
                                }
                            )
                            MarblePillChip(
                                label = trx("Best score"),
                                selected = settings.nodeSortMode == NodeSortMode.SCORE && !settings.nodeSortReverse,
                                accent = colors.secondary,
                                onClick = {
                                    repo.updateSettings(
                                        settings.copy(nodeSortMode = NodeSortMode.SCORE, nodeSortReverse = false)
                                    )
                                }
                            )
                            MarblePillChip(
                                label = trx("Name"),
                                selected = settings.nodeSortMode == NodeSortMode.NAME,
                                accent = colors.tertiary,
                                onClick = {
                                    repo.updateSettings(settings.copy(nodeSortMode = NodeSortMode.NAME))
                                }
                            )
                            MarblePillChip(
                                label = trx("Default order"),
                                selected = settings.nodeSortMode == NodeSortMode.DEFAULT,
                                onClick = {
                                    repo.updateSettings(
                                        settings.copy(nodeSortMode = NodeSortMode.DEFAULT, nodeSortReverse = false)
                                    )
                                }
                            )
                        }

                        MarbleChipStrip {
                            MarblePillChip(
                                label = strings.homeAllGroups,
                                selected = repo.librarySourceFilter == "all",
                                onClick = { repo.selectLibrarySource("all") }
                            )
                            repo.subscriptions.forEach { sub ->
                                MarblePillChip(
                                    label = sub.name,
                                    selected = repo.librarySourceFilter == sub.id,
                                    onClick = { repo.selectLibrarySource(sub.id) }
                                )
                            }
                            MarblePillChip(
                                label = strings.homeManualGroup,
                                selected = repo.librarySourceFilter == "manual",
                                onClick = { repo.selectLibrarySource("manual") }
                            )
                        }

                        if (protocolTallies.size > 1) {
                            MarbleChipStrip {
                                MarblePillChip(
                                    label = trx("All protocols"),
                                    selected = settings.serversProtocolFilter.isBlank(),
                                    accent = colors.secondary,
                                    onClick = { repo.updateSettings(settings.copy(serversProtocolFilter = "")) }
                                )
                                protocolTallies.forEach { tally ->
                                    MarblePillChip(
                                        label = "${tally.scheme} (${tally.count})",
                                        selected = settings.serversProtocolFilter.equals(tally.scheme, ignoreCase = true),
                                        accent = colors.secondary,
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
            }
        }

        if (repo.probeActive || repo.probeCancelling) {
            item(key = "servers-probe-progress") {
                val total = repo.probeTotal.coerceAtLeast(1)
                val done = repo.probeDone.coerceIn(0, total)
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
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
                            MarbleRowIcon(glyph = MarbleGlyph.GAUGE, tint = colors.success)
                            Column {
                                Text(
                                    text = "${trx("Testing servers")} (${faDigits("$done / $total")})",
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = repo.probeCurrentName.ifBlank { strings.measuring },
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        MarbleButton(
                            label = strings.cancel,
                            onClick = { repo.cancelProbes() },
                            style = MarbleButtonStyle.DESTRUCTIVE,
                            compact = true
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        LinearWavyProgressIndicator(
                            progress = { done.toFloat() / total.toFloat() },
                            color = colors.success,
                            trackColor = colors.surfaceContainer
                        )
                    }
                }
            }
        }

        if (allProfiles.isEmpty()) {
            item(key = "servers-empty") {
                MarbleEmptyState(
                    title = strings.noServersTitle,
                    body = strings.noServersBody,
                    glyph = MarbleGlyph.SERVER,
                    tint = colors.primary,
                    action = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MarbleButton(
                                label = strings.pasteShortcut,
                                glyph = MarbleGlyph.COPY,
                                onClick = pasteFromClipboard,
                                modifier = Modifier.weight(1f)
                            )
                            MarbleButton(
                                label = strings.qrShortcut,
                                glyph = MarbleGlyph.QR,
                                style = MarbleButtonStyle.TINTED,
                                onClick = onOpenQrImport,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        MarbleButton(
                            label = trx("Add Server or Subscription"),
                            glyph = MarbleGlyph.PLUS,
                            style = MarbleButtonStyle.SECONDARY,
                            onClick = onOpenAddSheet,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
        } else if (visibleProfiles.isEmpty()) {
            item(key = "servers-filtered-empty") {
                MarbleEmptyState(
                    title = trx("No matching servers"),
                    body = trx("Clear your search or filters to view all saved servers."),
                    glyph = MarbleGlyph.SEARCH,
                    tint = colors.secondary,
                    action = {
                        MarbleButton(
                            label = trx("Reset Filters"),
                            style = MarbleButtonStyle.TINTED,
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
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
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
                        },
                        pendingDeleteId = deleteTarget?.id.orEmpty(),
                        onSwipeDelete = { deleteTarget = it }
                    )
                }
            }
        }
            }
        }

        // MARBLE_M3_EXPRESSIVE_V242 — scrim + expressive speed-dial: dim the list behind the
        // menu, rotate the + into × with a bouncy spring, and cascade the options with
        // spring offsets so the dial never lands as a flat, unbalanced stack on the content.
        AnimatedVisibility(
            visible = fabExpanded,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { fabExpanded = false }
            )
        }

        // MARBLE_EXPRESSIVE_SHELL_V241 — the FAB lives above the dock's clearance line and
        // leaves the screen while the list is moving, so it never sits on the last row the
        // user is trying to read. It returns the moment scrolling stops, or when opened.
        AnimatedVisibility(
            visible = fabExpanded || !listState.isScrollInProgress,
            enter = fadeIn() + scaleIn(initialScale = 0.82f),
            exit = fadeOut() + scaleOut(targetScale = 0.82f),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
        FloatingActionButtonMenu(
            expanded = fabExpanded,
            button = {
                ToggleFloatingActionButton(
                    checked = fabExpanded,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        fabExpanded = it
                    }
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.PLUS,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(checkedProgress * 45f),
                        weight = 1.2f
                    )
                }
            },
            modifier = Modifier
                .padding(end = 20.dp, bottom = MarbleTheme.bottomClearance + 12.dp)
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    fabExpanded = false
                    onOpenAddSheet()
                },
                text = { Text(trx("Add Server or Subscription")) },
                icon = {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.PLUS,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    fabExpanded = false
                    onOpenQrImport()
                },
                text = { Text(trx("Scan QR")) },
                icon = {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.QR,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    fabExpanded = false
                    pasteFromClipboard()
                },
                text = { Text(strings.addFromClipboard) },
                icon = {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.COPY,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
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
        MarbleAlertDialog(
            title = trx("Rename Server"),
            onDismiss = { renameTarget = null },
            confirmLabel = trx("Save"),
            confirmEnabled = renameDraft.trim().isNotBlank(),
            onConfirm = {
                repo.renameProfile(profile.id, renameDraft.trim(), profile.subscriptionId)
                renameTarget = null
            }
        ) {
            MarbleTextField(
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

        MarbleBottomSheet(
            title = trx("Move Server"),
            subtitle = cleanServerName(profile.name),
            onDismiss = { moveTarget = null }
        ) {
            if (destinations.isEmpty()) {
                Text(
                    text = trx("Add another subscription group first to move this server."),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                MarbleCard {
                    destinations.forEachIndexed { index, (destId, destName) ->
                        if (index > 0) MarbleDivider()
                        MarbleListItem(
                            title = destName,
                            subtitle = trx("Move into this group"),
                            glyph = MarbleGlyph.FOLDER,
                            iconTint = colors.primary,
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
        MarbleAlertDialog(
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
                color = colors.onSurfaceVariant,
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
        MarbleAlertDialog(
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
                color = colors.onSurfaceVariant,
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
    onManageSubscription: (Subscription) -> Unit,
    pendingDeleteId: String,
    onSwipeDelete: (ProxyProfile) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val sub = bucket.subscription
    val usedBytes = if (sub != null) sub.uploadBytes + sub.downloadBytes else 0L
    val quotaSubtitle = faDigits(
        when {
        sub != null && sub.totalBytes > 0L ->
            "${bucket.profiles.size} ${strings.serversUnit} · ${formatBytes(usedBytes)} / ${formatBytes(sub.totalBytes)}"
        sub != null && sub.updatedAt > 0L ->
            "${bucket.profiles.size} ${strings.serversUnit} · ${trx(relativeTimeLabel(sub.updatedAt))}"
            else ->
                "${bucket.profiles.size} ${strings.serversUnit}"
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MarbleSectionHeader(
            title = bucket.title,
            badge = faDigits("${bucket.profiles.size}"),
            badgeColor = if (sub != null) colors.secondary else colors.tertiary,
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sub != null && sub.url.isNotBlank()) {
                        Text(
                            text = trx("Refresh"),
                            color = colors.primary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.clickable(enabled = !repo.busy) {
                                repo.refresh(sub.id)
                            }
                        )
                    }
                    if (bucket.profiles.isNotEmpty() && !bucket.id.startsWith("country:")) {
                        Text(
                            text = strings.connectionPing,
                            color = colors.success,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.clickable(enabled = !repo.probeActive) {
                                repo.testSource(bucket.id)
                            }
                        )
                    }
                    if (sub != null) {
                        Text(
                            text = trx("Edit"),
                            color = colors.onSurfaceVariant,
                            // MARBLE_WEIGHT_LADDER_V235 — Refresh/Ping/Edit are one row of peer
                            // actions: one rung (TITLE 600) for all three, color carries the rest.
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.clickable { onManageSubscription(sub) }
                        )
                    }
                }
            }
        )

        MarbleCard {
            MarbleListItem(
                title = bucket.title,
                subtitle = quotaSubtitle,
                glyph = if (sub != null) MarbleGlyph.LAYERS else MarbleGlyph.FOLDER,
                iconTint = if (sub != null) colors.secondary else colors.tertiary,
                showChevron = false,
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        MarbleGlyphIcon(
                            glyph = if (collapsed) MarbleGlyph.CHEVRON else MarbleGlyph.CHEVRON_DOWN,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                },
                onClick = onToggleCollapse
            )

            if (!collapsed) {
                bucket.profiles.forEachIndexed { index, profile ->
                    val active = repo.isActiveProfile(profile)
                    val selected = repo.isSelectedProfile(profile)
                    val result = benchmarks[profile.id]
                    val probeState = repo.probeStateOf(profile.id)
                    val countryCode = repo.serverLocation(profile).code

                    if (index > 0) MarbleDivider()
                    // V242: swipe/dismiss state belongs to the profile, not to the visual slot.
                    // Sorting or filtering must never hand a half-swiped row to another server.
                    key(profile.id) {
                        ServerRowItem(
                            profile = profile,
                            countryCode = countryCode,
                            active = active,
                            selected = selected,
                            result = result,
                            probeState = probeState,
                            swipeEnabled = !repo.probeActive && !repo.probeCancelling,
                            pendingDelete = pendingDeleteId == profile.id,
                            onClick = { onSelectProfile(profile) },
                            onMore = { onMoreProfile(profile) },
                            onSwipeDelete = { onSwipeDelete(profile) }
                        )
                    }
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
                            color = colors.onSurfaceVariant,
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
    swipeEnabled: Boolean,
    pendingDelete: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState()

    // The swipe only OPENS the confirm dialog: if the user backs out, the row
    // slides home again. (On confirm the row leaves with its profile anyway.)
    LaunchedEffect(pendingDelete, profile.id) {
        if (!pendingDelete && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // MARBLE_EXPRESSIVE_SHELL_V241 — the destructive reveal is a neutral tonal step
            // with error ink, not a filled error field: a swipe the user is still deciding
            // about must not paint the row like a system failure. There is no trash button on
            // the row either — the gesture is the affordance, and it confirms with a haptic.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceContainerHighest)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = trx("Delete"),
                        color = colors.error,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.TRASH,
                        color = colors.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = swipeEnabled,
        onDismiss = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onSwipeDelete()
        }
    ) {
        ServerRowContent(
            profile = profile,
            countryCode = countryCode,
            active = active,
            selected = selected,
            result = result,
            probeState = probeState,
            onClick = onClick,
            onMore = onMore
        )
    }
}

@Composable
internal fun ServerRowContent(
    profile: ProxyProfile,
    countryCode: String?,
    active: Boolean,
    selected: Boolean,
    result: BenchmarkResult?,
    probeState: ProbeState,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    // V242: the probe column is reserved before the first result arrives. A long localized
    // "checking" label must not push the name, the address or the next row sideways.
    val pingText = when (probeState) {
        ProbeState.TESTING -> strings.pingMeasuringValue
        ProbeState.QUEUED -> "…"
        ProbeState.IDLE -> trx(pingBadgeText(result, probeState))
    }
    val quietFailure = result != null && (result.success <= 0 || result.latencyMs <= 0.0)
    val health = when {
        probeState == ProbeState.TESTING || probeState == ProbeState.QUEUED -> ServerHealth.UNTESTED
        quietFailure -> ServerHealth.DEAD
        result == null -> ServerHealth.UNTESTED
        else -> serverHealthOf(result.latencyMs, result.success > 0)
    }
    val title = cleanServerName(profile.name).ifBlank { strings.node }
    val stateLabel = when {
        active -> trx("Active")
        selected -> trx("Selected")
        else -> ""
    }
    val container = when {
        active -> colors.successContainer.copy(alpha = 0.32f)
        selected -> colors.primaryContainer.copy(alpha = 0.30f)
        else -> Color.Transparent
    }
    val accent = when {
        active -> colors.success
        selected -> colors.primary
        else -> colors.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server-row-${profile.id}")
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(
                    title,
                    protocolShortLabel(profile),
                    profile.host.takeIf { it.isNotBlank() },
                    stateLabel.takeIf { it.isNotBlank() },
                    pingText
                ).joinToString(" · ")
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            MarbleServerAvatar(profile = profile, countryCode = countryCode, size = 44.dp)
            if (active || selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.CHECK,
                        color = if (active) colors.onSuccess else colors.onPrimary,
                        modifier = Modifier.size(11.dp),
                        weight = 1.25f
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("server-row-title-${profile.id}"),
                    color = if (quietFailure && !active && !selected) colors.onSurfaceVariant else accent,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MarbleIconButton(
                    glyph = MarbleGlyph.MORE,
                    onClick = onMore,
                    tint = colors.onSurfaceVariant,
                    contentDescription = "${trx("Server actions")}: $title",
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("server-row-more-${profile.id}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = protocolShortLabel(profile),
                        color = colors.primary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    if (profile.host.isNotBlank()) {
                        Text(
                            text = "·",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                        Text(
                            // FSI/PDI keep an IPv4/IPv6 literal in network order inside RTL copy.
                            text = "\u2068${profile.host}\u2069",
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("server-row-host-${profile.id}"),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                MarbleHealthPill(
                    health = health,
                    text = pingText,
                    modifier = Modifier
                        .width(88.dp)
                        .testTag("server-row-ping-${profile.id}")
                )
            }
        }
    }
}

@Composable
private fun ServersFilterSheet(
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val settings = repo.settings

    MarbleBottomSheet(
        title = trx("Sort & Filter"),
        subtitle = trx("Organize servers by latency, score, country or protocol"),
        onDismiss = onDismiss,
        scrollable = true
    ) {
        MarbleSectionHeader(title = trx("Sort Order"))
        MarbleCard {
            val sortChoices = listOf(
                NodeSortMode.DEFAULT to trx("Subscription order"),
                NodeSortMode.PING to trx("Lowest ping"),
                NodeSortMode.SCORE to trx("Highest smart score"),
                NodeSortMode.NAME to trx("Name (A–Z)"),
                NodeSortMode.COUNTRY to trx("Country"),
                NodeSortMode.PROTOCOL to trx("Protocol")
            )
            sortChoices.forEachIndexed { idx, (mode, label) ->
                if (idx > 0) MarbleDivider()
                MarbleChoiceRow(
                    title = label,
                    selected = settings.nodeSortMode == mode,
                    glyph = MarbleGlyph.FILTER,
                    iconTint = colors.primary,
                    onClick = { repo.updateSettings(settings.copy(nodeSortMode = mode)) }
                )
            }
        }

        MarbleSectionHeader(title = trx("Display Options"))
        MarbleCard {
            MarbleSwitchRow(
                title = trx("Reverse sort direction"),
                checked = settings.nodeSortReverse,
                glyph = MarbleGlyph.TUNERS,
                iconTint = colors.secondary,
                onCheckedChange = { repo.updateSettings(settings.copy(nodeSortReverse = it)) }
            )
            MarbleDivider()
            MarbleSwitchRow(
                title = trx("Group by country"),
                subtitle = trx("Organize servers by exit country instead of subscription"),
                checked = settings.serversGroupByCountry,
                glyph = MarbleGlyph.GLOBE,
                iconTint = colors.tertiary,
                onCheckedChange = { repo.updateSettings(settings.copy(serversGroupByCountry = it)) }
            )
            MarbleDivider()
            MarbleSwitchRow(
                title = trx("Only reachable servers"),
                subtitle = trx("Hide servers whose last test failed"),
                checked = settings.serversOnlyReachable,
                glyph = MarbleGlyph.CHECK,
                iconTint = colors.success,
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
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current

    MarbleBottomSheet(
        title = cleanServerName(profile.name),
        subtitle = "${protocolShortLabel(profile)} · ${profile.host}:${profile.port}",
        onDismiss = onDismiss,
        scrollable = true
    ) {
        MarbleCard {
            MarbleListItem(
                title = Tr.now.connect,
                subtitle = trx("Connect immediately through this server"),
                glyph = MarbleGlyph.POWER,
                iconTint = colors.success,
                onClick = onConnect
            )
            MarbleDivider()
            MarbleListItem(
                title = trx("Test Route Latency"),
                subtitle = trx("Run a full tunnel & delay test on this server"),
                glyph = MarbleGlyph.GAUGE,
                iconTint = colors.primary,
                onClick = {
                    onDismiss()
                    repo.fullTest(profile)
                }
            )
            MarbleDivider()
            MarbleListItem(
                title = trx("Server Details & JSON"),
                subtitle = trx("Inspect security, IPv4/IPv6 reachability and outbound JSON"),
                glyph = MarbleGlyph.TUNERS,
                iconTint = colors.secondary,
                onClick = onInspect
            )
            MarbleDivider()
            MarbleListItem(
                title = trx("Share QR Code"),
                subtitle = trx("Display scannable QR code or copy link"),
                glyph = MarbleGlyph.QR,
                iconTint = colors.secondary,
                onClick = onShareQr
            )
            MarbleDivider()
            MarbleListItem(
                title = trx("Copy Share Link / Config"),
                subtitle = trx("Copy URI or outbound JSON to clipboard"),
                glyph = MarbleGlyph.COPY,
                iconTint = colors.tertiary,
                onClick = {
                    val payload = profile.raw.ifBlank { profile.configJson }
                    clipboard.setText(AnnotatedString(payload))
                    repo.setRuntimeMessage("Config copied")
                    onDismiss()
                }
            )
            MarbleDivider()
            MarbleListItem(
                title = trx("Rename"),
                glyph = MarbleGlyph.PENCIL,
                iconTint = colors.warning,
                onClick = onRename
            )
            MarbleDivider()
            MarbleListItem(
                title = trx("Duplicate"),
                glyph = MarbleGlyph.LAYERS,
                iconTint = colors.tertiary,
                onClick = {
                    repo.duplicateProfile(profile.id, profile.subscriptionId)
                    onDismiss()
                }
            )
            MarbleDivider()
            MarbleListItem(
                title = trx("Move to Group"),
                glyph = MarbleGlyph.FOLDER,
                iconTint = colors.primary,
                onClick = onMove
            )
            MarbleDivider()
            MarbleListItem(
                title = trx("Delete Server"),
                titleColor = colors.error,
                glyph = MarbleGlyph.TRASH,
                iconTint = colors.error,
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
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val failedCount = repo.failedSubscriptionNodeCount(subscription.id, "TUNNEL")

    MarbleBottomSheet(
        title = subscription.name,
        subtitle = trx("Manage subscription URL, links and failed nodes"),
        onDismiss = onDismiss
    ) {
        MarbleTextField(
            label = trx("Subscription Name"),
            value = nameDraft,
            onValueChange = onNameChange
        )
        MarbleTextField(
            label = trx("HTTPS Subscription URL"),
            value = urlDraft,
            onValueChange = onUrlChange,
            placeholder = "https://..."
        )
        MarbleButton(
            label = trx("Save Changes"),
            onClick = onSave,
            enabled = nameDraft.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        MarbleCard {
            MarbleListItem(
                title = trx("Copy All Server Links"),
                subtitle = trx("Copy every server in this subscription to clipboard"),
                glyph = MarbleGlyph.COPY,
                iconTint = colors.primary,
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
                MarbleDivider()
                MarbleListItem(
                    title = "${trx("Remove Failed Servers")} ($failedCount)",
                    subtitle = trx("Prune unreachable nodes from this subscription"),
                    glyph = MarbleGlyph.TRASH,
                    iconTint = colors.warning,
                    onClick = {
                        repo.removeFailedSubscriptionNodes(subscription.id, "TUNNEL")
                        onDismiss()
                    }
                )
            }
            MarbleDivider()
            MarbleListItem(
                title = trx("Delete Subscription"),
                titleColor = colors.error,
                glyph = MarbleGlyph.TRASH,
                iconTint = colors.error,
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
    val colors = MaterialTheme.colorScheme
    val payload = profile.raw.ifBlank { profile.configJson }
    val qr = remember(payload) { runCatching { QrCode.encode(payload) }.getOrNull() }

    MarbleBottomSheet(
        title = cleanServerName(profile.name),
        subtitle = protocolShortLabel(profile),
        onDismiss = onDismiss
    ) {
        MarbleCard(contentPadding = PaddingValues(24.dp)) {
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
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                MarbleButton(
                    label = trx("Copy Share Link"),
                    glyph = MarbleGlyph.COPY,
                    onClick = { onCopy(payload) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
