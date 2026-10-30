package com.marbleng.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.marbleng.app.core.PendingServerRemoval
import com.marbleng.app.core.PingReadout
import com.marbleng.app.core.QrCode
import com.marbleng.app.core.ServersDeck
import com.marbleng.app.core.ServersFilter
import com.marbleng.app.core.ServersFilterChip
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.core.ServersSelection
import com.marbleng.app.core.ServersUndo
import com.marbleng.app.core.activeFilterChips
import com.marbleng.app.core.pingBars
import com.marbleng.app.core.pingReadout
import com.marbleng.app.core.protocolChip
import com.marbleng.app.core.transportLine
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.NodeSortMode
import com.marbleng.app.model.ProbeState
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.ServerHealth
import com.marbleng.app.model.Subscription
import com.marbleng.app.model.serverHealthOf
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class ServerGroupBucket(
    val id: String,
    val title: String,
    val subscription: Subscription?,
    val profiles: List<ProxyProfile>
)

/**
 * MARBLE_SERVERS_DECK_V243 — the Servers page is a list of servers, not a wall of chrome.
 *
 * What this page is now, top to bottom:
 *
 * 1. one header line with the title and two actions (test every server, add);
 * 2. one search line whose filter action opens a bottom sheet carrying sort, protocol and group;
 * 3. the compact chip row — only when a filter is really narrowing something;
 * 4. the pinned "active server" card while a tunnel is up, so the page always answers
 *    "what am I connected to?";
 * 5. collapsible group sections with **one** overflow action each, and no header at all when
 *    there is only one group;
 * 6. compact 68dp rows: protocol chip, name, "TLS · XHTTP", ping with semantic colour and a
 *    four-level bar, and one 48dp overflow target. No address (it lives in the details page), no
 *    Delete label (the gesture is the affordance), no country flag repeated twelve times.
 *
 * The column arithmetic, the ping levels, the transport line, the selection roster and the undo
 * window are pure functions in [ServersDeck]; this file only draws their answers.
 */
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
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
    var batchDeleteTargets by remember { mutableStateOf<List<ProxyProfile>>(emptyList()) }
    var editSubscription by remember { mutableStateOf<Subscription?>(null) }
    var editSubName by remember { mutableStateOf("") }
    var editSubUrl by remember { mutableStateOf("") }
    var deleteSubscription by remember { mutableStateOf<Subscription?>(null) }
    var collapsedGroups by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var openGroupMenu by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var pendingRemoval by remember { mutableStateOf<PendingServerRemoval?>(null) }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

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
    val hiddenIds = ServersUndo.hiddenIds(pendingRemoval)
    val visibleProfiles = ServersQuery.sort(
        profiles = ServersQuery.visible(allProfiles, filter, benchmarks)
            .filterNot { it.id in hiddenIds },
        mode = settings.nodeSortMode,
        reverse = settings.nodeSortReverse,
        benchmarks = benchmarks
    )
    val protocolTallies = ServersQuery.protocolTallies(
        allProfiles.filter { filter.sourceId == "all" || it.subscriptionId == filter.sourceId }
    )
    val sourceName = repo.subscriptions.firstOrNull { it.id == repo.librarySourceFilter }?.name
        ?: if (repo.librarySourceFilter == "manual") strings.homeManualGroup else null
    val filterChips: List<ServersFilterChip> = activeFilterChips(
        filter = filter,
        sourceName = sourceName,
        reversed = settings.nodeSortReverse
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
                if (nodes.isNotEmpty()) {
                    add(ServerGroupBucket(id = sub.id, title = sub.name, subscription = sub, profiles = nodes))
                }
            }
            val manualNodes = visibleProfiles.filter { it.subscriptionId == "manual" }
            if (manualNodes.isNotEmpty()) {
                add(
                    ServerGroupBucket(
                        id = "manual",
                        title = strings.homeManualGroup,
                        subscription = null,
                        profiles = manualNodes
                    )
                )
            }
        }
    }
    val showGroupHeaders = ServersDeck.showsGroupHeaders(groups.size)
    val activeRoute = repo.profile(repo.activeProfileId)
    val routeLive = repo.state == "CONNECTED" || repo.state == "CONNECTING" || repo.state == "DISCONNECTING"

    val selectOrConnect: (ProxyProfile) -> Unit = { profile ->
        if (repo.probeActive || repo.probeCancelling) {
            repo.setRuntimeMessage("Wait until ping finishes before changing server")
        } else if (repo.state == "CONNECTED" || repo.state == "CONNECTING") {
            onConnect(profile)
        } else {
            repo.selectProfile(profile)
        }
    }

    // Copy resolved once, in the composition, so the coroutine below never needs a composable
    // call from a non-composable context.
    val undoLabel = trx("Undo")
    val removedLabel = trx("removed")
    val selectionScopeLabel = trx("Selected servers")

    /**
     * MARBLE_SERVERS_DECK_V243 — a swipe delete is a promise, not a fact. The row leaves the page
     * at once, the snackbar holds the truth for [ServersDeck.UNDO_WINDOW_MS], and only the end of
     * that window calls the repository. Undo simply drops the pending removal.
     */
    fun settlePendingRemoval(commit: Boolean) {
        val entry = pendingRemoval ?: return
        pendingRemoval = null
        if (commit) repo.removeProfile(entry.profileId, entry.subscriptionId)
    }

    fun scheduleRemoval(profile: ProxyProfile) {
        settlePendingRemoval(commit = true)
        val entry = ServersUndo.schedule(profile, System.currentTimeMillis())
        pendingRemoval = entry
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "${cleanServerName(profile.name)} · $removedLabel",
                actionLabel = undoLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            val current = pendingRemoval
            if (current != null && current.profileId == entry.profileId) {
                pendingRemoval = null
                if (result != SnackbarResult.ActionPerformed) {
                    repo.removeProfile(current.profileId, current.subscriptionId)
                }
            }
        }
    }

    val visibleIds = visibleProfiles.map { it.id }
    val selectedProfiles = visibleProfiles.filter { it.id in selection }

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
                    top = 4.dp,
                    // V243: the extended FAB floats over this page, so the list keeps a full
                    // FAB's worth of slack plus the dock clearance. The last server can never
                    // sit under the button again.
                    bottom = MarbleTheme.bottomClearance + ServersDeck.FAB_CLEARANCE_DP.dp
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
                            contentDescription = trx("Test every server"),
                            onClick = {
                                if (repo.probeActive || repo.probeCancelling) {
                                    repo.cancelProbes()
                                } else {
                                    repo.testSource(repo.librarySourceFilter)
                                }
                            }
                        )
                        MarbleIconButton(
                            glyph = MarbleGlyph.PLUS,
                            tint = colors.primary,
                            contentDescription = trx("Add Server or Subscription"),
                            onClick = onOpenAddSheet
                        )
                    }
                }

                // V243 — search is one line. Sort, protocol and group live behind the filter
                // action in a sheet: three chip strips above the first server were 40 % of the
                // page spent on questions the user had not asked yet.
                item(key = "servers-search") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarbleSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = strings.searchServersHint,
                            modifier = Modifier.weight(1f)
                        )
                        MarbleIconButton(
                            glyph = MarbleGlyph.FILTER,
                            tint = if (filterChips.isNotEmpty()) colors.warning else colors.onSurfaceVariant,
                            contentDescription = trx("Sort & Filter"),
                            onClick = { filterSheetOpen = true }
                        )
                    }
                }

                if (ServersDeck.showsFilterStrip(filterChips.size)) {
                    item(key = "servers-active-filters") {
                        MarbleChipStrip {
                            filterChips.forEach { chip ->
                                MarblePillChip(
                                    label = trx(chip.label),
                                    selected = true,
                                    accent = colors.warning,
                                    onClick = { clearFilterChip(repo, settings, chip) }
                                )
                            }
                            MarblePillChip(
                                label = trx("Reset Filters"),
                                selected = false,
                                onClick = {
                                    searchQuery = ""
                                    repo.selectLibrarySource("all")
                                    repo.updateSettings(
                                        settings.copy(
                                            serversProtocolFilter = "",
                                            serversOnlyReachable = false,
                                            serversMaxPingMs = 0,
                                            nodeSortReverse = false
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                if (routeLive && activeRoute != null) {
                    item(key = "servers-active-route") {
                        ActiveServerCard(
                            repo = repo,
                            profile = activeRoute,
                            state = repo.state,
                            livePingMs = repo.livePingMs,
                            onInspect = { onOpenDetail(activeRoute) },
                            onDisconnect = { repo.stopVpn() }
                        )
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
                        if (showGroupHeaders) {
                            item(key = "group-header-${bucket.id}") {
                                ServerGroupHeader(
                                    bucket = bucket,
                                    collapsed = isCollapsed,
                                    busy = repo.busy,
                                    probeActive = repo.probeActive,
                                    menuOpen = openGroupMenu == bucket.id,
                                    onMenuOpenChange = { open ->
                                        openGroupMenu = if (open) bucket.id else ""
                                    },
                                    onToggleCollapse = {
                                        collapsedGroups = if (isCollapsed) {
                                            collapsedGroups - bucket.id
                                        } else {
                                            collapsedGroups + bucket.id
                                        }
                                    },
                                    onRefresh = { repo.refresh(bucket.id) },
                                    onPing = { repo.testSource(bucket.id) },
                                    onEdit = bucket.subscription?.let { sub ->
                                        val openGroupEditor: () -> Unit = {
                                            openGroupMenu = ""
                                            editSubscription = sub
                                            editSubName = sub.name
                                            editSubUrl = sub.url
                                        }
                                        openGroupEditor
                                    }
                                )
                            }
                        }
                        item(key = "group-section-${bucket.id}") {
                            ServerGroupCard(
                                repo = repo,
                                bucket = bucket,
                                collapsed = isCollapsed && showGroupHeaders,
                                benchmarks = benchmarks,
                                selection = selection,
                                onSelectProfile = selectOrConnect,
                                onMoreProfile = { actionTarget = it },
                                onLongPressProfile = { profile ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selection = ServersSelection.toggle(selection, profile.id)
                                },
                                pendingDeleteId = deleteTarget?.id
                                    ?: pendingRemoval?.profileId.orEmpty(),
                                onSwipeDelete = { scheduleRemoval(it) }
                            )
                        }
                    }
                }
            }
        }

        // V243 — a contextual bar for the multi-select roster, floating over the list so it is
        // reachable wherever the user long-pressed, and never a second header row in the flow.
        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            SelectionBar(
                count = selection.size,
                allSelected = ServersSelection.allSelected(selection, visibleIds),
                onToggleAll = { selection = ServersSelection.toggleAll(selection, visibleIds) },
                onPing = {
                    if (selectedProfiles.isNotEmpty()) {
                        repo.pingProfiles(
                            profiles = selectedProfiles,
                            scopeLabel = selectionScopeLabel,
                            scopeId = "selection"
                        )
                    }
                    selection = emptySet()
                },
                onDelete = { batchDeleteTargets = selectedProfiles },
                onClose = { selection = emptySet() }
            )
        }

        // V243 — an extended FAB that folds to its icon while the page moves. It sits above the
        // dock clearance and the list reserves its height, so it covers no ping.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = MarbleTheme.bottomClearance + 12.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.primaryContainer)
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onOpenAddSheet()
                }
                .heightIn(min = 56.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarbleGlyphIcon(
                glyph = MarbleGlyph.PLUS,
                color = colors.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
                weight = 1.2f
            )
            AnimatedVisibility(
                visible = !listState.isScrollInProgress,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = trx("Add"),
                    color = colors.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = MarbleTheme.bottomClearance + 76.dp
                )
        )
    }

    if (filterSheetOpen) {
        ServersFilterSheet(
            repo = repo,
            protocolTallies = protocolTallies,
            onManageSubscription = { sub ->
                filterSheetOpen = false
                editSubscription = sub
                editSubName = sub.name
                editSubUrl = sub.url
            },
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

    if (batchDeleteTargets.isNotEmpty()) {
        MarbleAlertDialog(
            title = trx("Delete selected servers?"),
            onDismiss = { batchDeleteTargets = emptyList() },
            confirmLabel = trx("Delete"),
            destructive = true,
            onConfirm = {
                batchDeleteTargets.forEach { repo.removeProfile(it.id, it.subscriptionId) }
                batchDeleteTargets = emptyList()
                selection = emptySet()
            }
        ) {
            Text(
                text = "${faDigits("${batchDeleteTargets.size}")} ${strings.serversUnit}",
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

/** One chip removed from the sticky strip, with everything else left alone. */
private fun clearFilterChip(
    repo: AppRepository,
    settings: com.marbleng.app.model.AppSettings,
    chip: ServersFilterChip
) {
    when (chip.key) {
        "protocol" -> repo.updateSettings(settings.copy(serversProtocolFilter = ""))
        "source" -> repo.selectLibrarySource("all")
        "reachable" -> repo.updateSettings(settings.copy(serversOnlyReachable = false))
        "maxPing" -> repo.updateSettings(settings.copy(serversMaxPingMs = 0))
        "reverse" -> repo.updateSettings(settings.copy(nodeSortReverse = false))
    }
}

/**
 * The pinned answer to "what am I connected to?": name, protocol, live ping and one way out.
 * It is the only card on this page allowed to shout, because it is the only one describing a
 * live tunnel rather than a stored row.
 */
@Composable
private fun ActiveServerCard(
    repo: AppRepository,
    profile: ProxyProfile,
    state: String,
    livePingMs: Int,
    onInspect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val connected = state == "CONNECTED"
    val health = when {
        !connected -> ServerHealth.UNTESTED
        livePingMs <= 0 -> ServerHealth.UNTESTED
        else -> serverHealthOf(livePingMs.toDouble(), true)
    }
    val subtitle = listOfNotNull(
        protocolShortLabel(profile),
        transportLine(profile).takeIf { it.isNotBlank() },
        repo.serverLocation(profile).code.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    MarbleCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onInspect)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MarblePulseDot(color = if (connected) colors.success else colors.warning, modifier = Modifier.size(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (connected) trx("Active server") else trx("Connecting"),
                    color = colors.success,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = cleanServerName(profile.name).ifBlank { strings.node },
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MarbleHealthPill(
                health = health,
                text = if (livePingMs > 0) faDigits("$livePingMs ms") else strings.pingIdleValue
            )
        }
        Spacer(Modifier.height(10.dp))
        MarbleButton(
            label = strings.disconnect,
            glyph = MarbleGlyph.POWER,
            style = MarbleButtonStyle.TINTED,
            colorOverride = colors.error,
            compact = true,
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The multi-select roster, as a floating tonal bar with three decisions. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = faDigits("$count") + " " + trx("selected"),
            color = colors.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        MarbleIconButton(
            glyph = MarbleGlyph.CHECK,
            tint = if (allSelected) colors.success else colors.onSurfaceVariant,
            contentDescription = trx("Select all"),
            onClick = onToggleAll
        )
        MarbleIconButton(
            glyph = MarbleGlyph.GAUGE,
            tint = colors.success,
            contentDescription = trx("Ping selected"),
            onClick = onPing
        )
        MarbleIconButton(
            glyph = MarbleGlyph.TRASH,
            tint = colors.error,
            contentDescription = trx("Delete"),
            onClick = onDelete
        )
        MarbleIconButton(
            glyph = MarbleGlyph.CLOSE,
            tint = colors.onSurfaceVariant,
            contentDescription = trx("Cancel"),
            onClick = onClose
        )
    }
}

/**
 * One collapsible section header: title, count, freshness and a single overflow action.
 *
 * V243 — Refresh / Ping / Edit used to be three green words of label text on the header line:
 * too small to hit, too loud to ignore, and duplicated by the icons at the top of the page.
 * They are one 48dp overflow target now.
 */
@Composable
private fun ServerGroupHeader(
    bucket: ServerGroupBucket,
    collapsed: Boolean,
    busy: Boolean,
    probeActive: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onToggleCollapse: () -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onEdit: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val sub = bucket.subscription
    val freshness = sub?.updatedAt?.takeIf { it > 0L }?.let { trx(relativeTimeLabel(it)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleCollapse)
            .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MarbleGlyphIcon(
            glyph = if (collapsed) MarbleGlyph.CHEVRON else MarbleGlyph.CHEVRON_DOWN,
            color = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = bucket.title,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        MarbleBadge(
            text = faDigits("${bucket.profiles.size}"),
            color = if (sub != null) colors.secondary else colors.tertiary
        )
        if (freshness != null) {
            Text(
                text = freshness,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            MarbleIconButton(
                glyph = MarbleGlyph.MORE,
                tint = colors.onSurfaceVariant,
                contentDescription = "${trx("Group actions")}: ${bucket.title}",
                onClick = { onMenuOpenChange(true) }
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
                containerColor = colors.surfaceContainerHigh
            ) {
                if (sub != null && sub.url.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text(trx("Refresh"), style = MaterialTheme.typography.bodyMedium) },
                        enabled = !busy,
                        leadingIcon = {
                            MarbleGlyphIcon(
                                glyph = MarbleGlyph.REFRESH,
                                color = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            onMenuOpenChange(false)
                            onRefresh()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(strings.connectionPing, style = MaterialTheme.typography.bodyMedium) },
                    enabled = !probeActive && bucket.profiles.isNotEmpty(),
                    leadingIcon = {
                        MarbleGlyphIcon(
                            glyph = MarbleGlyph.GAUGE,
                            color = colors.success,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onPing()
                    }
                )
                if (onEdit != null) {
                    DropdownMenuItem(
                        text = { Text(trx("Edit"), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            MarbleGlyphIcon(
                                glyph = MarbleGlyph.PENCIL,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            onMenuOpenChange(false)
                            onEdit()
                        }
                    )
                }
            }
        }
    }
}

/**
 * The group body: rows only. The card no longer repeats the group's own name, count and address
 * under the header that already said them.
 */
@Composable
private fun ServerGroupCard(
    repo: AppRepository,
    bucket: ServerGroupBucket,
    collapsed: Boolean,
    benchmarks: Map<String, BenchmarkResult>,
    selection: Set<String>,
    onSelectProfile: (ProxyProfile) -> Unit,
    onMoreProfile: (ProxyProfile) -> Unit,
    onLongPressProfile: (ProxyProfile) -> Unit,
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
            else -> "${bucket.profiles.size} ${strings.serversUnit}"
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // With one group there is no header, so the subscription's quota line still needs a home.
        if (sub != null && sub.totalBytes > 0L) {
            MarbleSectionFooter(text = quotaSubtitle)
        }
        MarbleCard(contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
            if (collapsed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "${faDigits("${bucket.profiles.size}")} ${strings.serversUnit} · ${trx("Collapsed")}",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                bucket.profiles.forEachIndexed { index, profile ->
                    val active = repo.isActiveProfile(profile)
                    val selected = repo.isSelectedProfile(profile)
                    val result = benchmarks[profile.id]
                    val probeState = repo.probeStateOf(profile.id)
                    val countryCode = repo.serverLocation(profile).code

                    if (index > 0) MarbleDivider(insetStart = 12.dp, insetEnd = 12.dp)
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
                            swipeEnabled = !repo.probeActive && !repo.probeCancelling && selection.isEmpty(),
                            pendingDelete = pendingDeleteId == profile.id,
                            selectionMode = selection.isNotEmpty(),
                            checked = profile.id in selection,
                            onClick = { onSelectProfile(profile) },
                            onMore = { onMoreProfile(profile) },
                            onLongPress = { onLongPressProfile(profile) },
                            onSwipeDelete = { onSwipeDelete(profile) }
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
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dismissState = rememberSwipeToDismissBoxState()

    // The swipe only OPENS the delete: the row slides back if the user changes their mind, and
    // the row leaves with its own profile when the undo window starts.
    LaunchedEffect(pendingDelete, profile.id) {
        if (!pendingDelete && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // V241/V243: the destructive reveal is a neutral tonal step with error ink, and the
            // label gets its own padding so it can never be clipped to "Delet" again.
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
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
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
            // HapticFeedbackType.LongPress is the "this removed something" pulse, not a tap.
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
            onMore = onMore,
            selectionMode = selectionMode,
            checked = checked,
            onLongPress = onLongPress
        )
    }
}

/**
 * The compact server row.
 *
 * | zone | content |
 * |---|---|
 * | start | 3dp state bar (active/selected/checked), then the protocol chip |
 * | middle | name (bold) + "TLS · XHTTP" — never the address, never the group name |
 * | ping | reserved 88dp: four semantic bars + the number, or a skeleton while measuring |
 * | end | one 48dp overflow target |
 *
 * There is no Delete control here by design: the row is a server, and the delete lives behind
 * the swipe (with an undo) or the overflow sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ServerRowContent(
    profile: ProxyProfile,
    countryCode: String?,
    active: Boolean,
    selected: Boolean,
    result: BenchmarkResult?,
    probeState: ProbeState,
    onClick: () -> Unit,
    onMore: () -> Unit,
    selectionMode: Boolean = false,
    checked: Boolean = false,
    onLongPress: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val health = when {
        probeState == ProbeState.TESTING || probeState == ProbeState.QUEUED -> ServerHealth.UNTESTED
        result != null && (result.success <= 0 || result.latencyMs <= 0.0) -> ServerHealth.DEAD
        result == null -> ServerHealth.UNTESTED
        else -> serverHealthOf(result.latencyMs, result.success > 0)
    }
    val title = cleanServerName(profile.name).ifBlank { strings.node }
    val transport = transportLine(profile)
    val subtitle = when {
        transport.isNotBlank() -> transport
        countryCode.isNullOrBlank() -> trx("TCP")
        else -> countryCode
    }
    val stateLabel = when {
        active -> trx("Active")
        selected -> trx("Selected")
        checked -> trx("Checked")
        else -> ""
    }
    val container = when {
        active -> colors.secondaryContainer.copy(alpha = 0.55f)
        selected || checked -> colors.secondaryContainer.copy(alpha = 0.32f)
        else -> Color.Transparent
    }
    val accentBar = when {
        active -> colors.success
        selected || checked -> colors.primary
        else -> null
    }
    val accent = when {
        active -> colors.success
        selected || checked -> colors.primary
        else -> colors.onSurface
    }
    val protocolTone = protocolRowColor(profile.scheme)
    // Read in the composition: a semantics lambda is not a composable context.
    val spokenPing = pingBadgeText(result, probeState)
    val spokenAddress = "\u2068${profile.host}\u2069".takeIf { profile.host.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server-row-${profile.id}")
            .heightIn(min = ServersDeck.ROW_MIN_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            )
            .semantics {
                contentDescription = listOfNotNull(
                    title,
                    protocolShortLabel(profile),
                    // FSI/PDI keep an IPv4/IPv6 literal in network order inside RTL copy. The
                    // address is spoken here and drawn in the details page, not repeated twice
                    // on the face of the row.
                    spokenAddress,
                    stateLabel.takeIf { it.isNotBlank() },
                    spokenPing
                ).joinToString(" · ")
            }
            .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (accentBar != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(accentBar)
            )
        }

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.CHECK,
                        color = colors.onPrimary,
                        modifier = Modifier.size(13.dp),
                        weight = 1.25f
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(protocolTone.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = protocolChip(profile),
                    color = protocolTone,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("server-row-title-${profile.id}"),
                    color = accent,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (active) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.CHECK,
                        color = colors.success,
                        modifier = Modifier.size(13.dp),
                        weight = 1.3f
                    )
                }
            }
            Text(
                text = subtitle,
                modifier = Modifier.testTag("server-row-transport-${profile.id}"),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        ServerPingCell(
            result = result,
            probeState = probeState,
            health = health,
            // 88dp is ServersDeck.PING_COLUMN_WIDTH_DP: the column is reserved before the
            // first measurement, so a result never shifts the row.
            modifier = Modifier
                .width(88.dp)
                .testTag("server-row-ping-${profile.id}")
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
}

/**
 * The ping, promoted to the most important number on the row: a four-level bar for the glance,
 * the number for the fact, in one column that is reserved before the first measurement.
 */
@Composable
private fun ServerPingCell(
    result: BenchmarkResult?,
    probeState: ProbeState,
    health: ServerHealth,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val measuring = probeState == ProbeState.TESTING || probeState == ProbeState.QUEUED
    val readout = when {
        measuring -> PingReadout.UNKNOWN
        result == null -> PingReadout.UNKNOWN
        else -> pingReadout(result.latencyMs, result.success > 0)
    }
    val tone = when (readout) {
        PingReadout.FAST -> colors.success
        PingReadout.OK -> colors.warning
        PingReadout.SLOW -> colors.error
        PingReadout.TIMEOUT -> colors.onSurfaceVariant.copy(alpha = 0.72f)
        PingReadout.UNKNOWN -> marbleHealthColor(health)
    }
    val text = when {
        measuring -> ""
        result == null -> strings.pingIdleValue
        readout == PingReadout.TIMEOUT -> strings.pingFailedShort
        readout == PingReadout.UNKNOWN -> strings.pingIdleValue
        else -> faDigits(result.latencyMs.roundToInt().toString())
    }
    val reported = readout == PingReadout.FAST ||
        readout == PingReadout.OK ||
        readout == PingReadout.SLOW
    val unit = if (text.isNotEmpty() && reported) " ms" else ""

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (measuring) {
            PingSkeleton()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PingBars(readout = readout, tone = tone)
                Text(
                    text = text + unit,
                    color = tone,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }
}

/** Four bars, lit from the bottom. A timeout lights none: no reply is not a weak reply. */
@Composable
private fun PingBars(readout: PingReadout, tone: Color) {
    val colors = MaterialTheme.colorScheme
    val lit = pingBars(readout)
    val heights = listOf(4.dp, 7.dp, 10.dp, 13.dp)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        heights.forEachIndexed { index, barHeight ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(
                        if (index < lit) tone else colors.onSurfaceVariant.copy(alpha = 0.16f)
                    )
            )
        }
    }
}

/** Skeleton shimmer while a probe is in flight — never a frozen number pretending to be truth. */
@Composable
private fun PingSkeleton() {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "pingSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pingSkeletonAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.onSurfaceVariant.copy(alpha = alpha * 0.6f))
        )
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.onSurfaceVariant.copy(alpha = alpha))
        )
    }
}

/**
 * Sort, protocol, group and the one group action set, in a sheet instead of four rows of page.
 */
@Composable
private fun ServersFilterSheet(
    repo: AppRepository,
    protocolTallies: List<com.marbleng.app.core.ProtocolTally>,
    onManageSubscription: (Subscription) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val settings = repo.settings
    val selectedSubscription = repo.subscriptions.firstOrNull { it.id == repo.librarySourceFilter }

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
                NodeSortMode.SCORE to trx("Best score"),
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

        MarbleSectionHeader(title = trx("Group"))
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
            MarbleSectionHeader(title = trx("Protocol"))
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

        if (selectedSubscription != null) {
            MarbleSectionHeader(title = trx("This group"))
            MarbleCard {
                MarbleListItem(
                    title = trx("Refresh this group"),
                    subtitle = trx("Fetch the subscription again"),
                    glyph = MarbleGlyph.REFRESH,
                    iconTint = colors.primary,
                    enabled = !repo.busy,
                    onClick = { repo.refresh(selectedSubscription.id) }
                )
                MarbleDivider()
                MarbleListItem(
                    title = strings.connectionPing,
                    subtitle = trx("Test every server in this group"),
                    glyph = MarbleGlyph.GAUGE,
                    iconTint = colors.success,
                    enabled = !repo.probeActive,
                    onClick = { repo.testSource(selectedSubscription.id) }
                )
                MarbleDivider()
                MarbleListItem(
                    title = trx("Edit subscription"),
                    subtitle = selectedSubscription.name,
                    glyph = MarbleGlyph.PENCIL,
                    iconTint = colors.onSurfaceVariant,
                    onClick = { onManageSubscription(selectedSubscription) }
                )
            }
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
