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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.PendingServerRemoval
import com.marbleng.app.core.PingReadout
import com.marbleng.app.core.QrCode
import com.marbleng.app.core.ServersAtlas
import com.marbleng.app.core.ServersDeck
import com.marbleng.app.core.ServersFilter
import com.marbleng.app.core.ServersFilterChip
import com.marbleng.app.core.ServersLibraryCard
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.core.ServersRowSurface
import com.marbleng.app.core.ServersSelection
import com.marbleng.app.core.ServersUndo
import com.marbleng.app.core.activeFilterChips
import com.marbleng.app.core.libraryCards
import com.marbleng.app.core.pingBars
import com.marbleng.app.core.pingReadout
import com.marbleng.app.core.protocolChip
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.NodeSortMode
import com.marbleng.app.model.ProbeState
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.ServerHealth
import com.marbleng.app.model.Subscription
import com.marbleng.app.model.serverHealthOf
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.successContainer
import com.marbleng.app.ui.theme.onSuccessContainer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal data class ServerGroupBucket(
    val id: String,
    val title: String,
    val subscription: Subscription?,
    val profiles: List<ProxyProfile>,
    val countryCode: String = ""
)

/**
 * MARBLE_SERVERS_ATLAS_V244 — the Servers page is an atlas: libraries, places, and one live route.
 *
 * V243 emptied the page until it was a list of servers. The twelve-seat design council that
 * reviewed it found the list honest and anonymous: twelve identical rows, a country the app had
 * already measured living in a settings page, a subscription you could only change through a
 * filter sheet, two "add" affordances, and a live-tunnel card louder than the servers the page
 * exists to choose between. V244 draws the library and the map:
 *
 * 1. one header line: the title, the measured library line ("32 servers · 12 located"), one
 *    test action and one **Add** pill — the floating add button is gone;
 * 2. the live route as a single strip (flag, name, live ping, one stop target) while a tunnel is
 *    up or connecting — never a full-width Disconnect competing with Home;
 * 3. one search line, with a sort menu and the filter sheet one tap apart;
 * 4. the **sources rail**: every subscription as a card with its plan, its node count and its
 *    measured flag cluster, one tap to switch, one sheet to manage;
 * 5. quiet section labels (flag, name, count, one overflow) only when the page really has more
 *    than one section, and only when a section is not folded;
 * 6. compact rows that wear their **measured flag** (or the protocol monogram before the sweep
 *    answers), the country and transport line, a reserved ping column and one 48dp overflow.
 *
 * The row arithmetic, the flag cluster, the plan bar, the sources model and the row's own
 * subtitle are pure functions in [ServersAtlas]; the ping levels, the transport line, the
 * selection roster and the undo window stay in [ServersDeck]. This file only draws their answers.
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
    var librarySheetOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }
    // V244 — the flags are the page's identity, so the sweep starts when the page opens instead
    // of waiting for a connect, a refresh or a trip to Settings. It is the same idempotent
    // repository sweep the rest of the app triggers; only the queue is empty is a no-op.
    LaunchedEffect(repo) { repo.ensureServerLocations() }

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
                profiles = bucket.profiles,
                countryCode = bucket.country.code
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
    // V244 — one rail model, consumed by the rail and by the sources sheet, so the two can never
    // disagree about what a library is called or how many servers it holds.
    val sourceCards = libraryCards(
        subscriptions = repo.subscriptions,
        profiles = allProfiles,
        countryCodeOf = { profile -> repo.serverLocation(profile).code },
        allTitle = trx("All sources"),
        manualTitle = strings.homeManualGroup
    )
    val activeRoute = repo.profile(repo.activeProfileId)
    val routeLive = repo.state == "CONNECTED" || repo.state == "CONNECTING" || repo.state == "DISCONNECTING"

    // Resolved in the composition: the connect lambda is not a composable context.
    val pingBusyLabel = trx("Wait until ping finishes before changing server")

    val selectOrConnect: (ProxyProfile) -> Unit = { profile ->
        if (repo.probeActive || repo.probeCancelling) {
            repo.setRuntimeMessage(pingBusyLabel)
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
                // MARBLE_EXPRESSIVE_PAGES_V251 — the shell's ambient light is this page's ground.
                .statusBarsPadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    top = 4.dp,
                    // V244 — the extended "Add" FAB is gone: adding is the header's pill and the
                    // empty state's primary action. The list no longer pays the whole
                    // ServersDeck.FAB_CLEARANCE_DP for a button that was never over it.
                    bottom = MarbleTheme.bottomClearance + 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                item(key = "servers-header") {
                    val located = repo.serverLocationSummary()
                    val locatedCount = located.first
                    val libraryCount = allProfiles.size
                    val headerSubtitle = when {
                        repo.serverLocationScanning ->
                            "${trx("Scanning flags")} · ${faDigits("$locatedCount/$libraryCount")}"

                        ServersAtlas.showsLocationNotice(locatedCount) ->
                            "${faDigits("$libraryCount")} ${strings.serversUnit} · " +
                                "${faDigits("$locatedCount")} ${trx("located")}"

                        else -> "${faDigits("$libraryCount")} ${strings.serversUnit}"
                    }
                    MarbleLargeHeader(
                        title = strings.serversTitle,
                        subtitle = headerSubtitle,
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
                        AddServerPill(onClick = onOpenAddSheet)
                    }
                }

                // V244 — the live route is the first answer on the page, not a card the reader
                // has to scroll past a filter rail to find.
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

                item(key = "servers-search") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarbleSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = strings.searchServersHint,
                            modifier = Modifier.weight(1f)
                        )
                        // MARBLE_IP_IDENTITY_V251 — this slot used to hold a sort menu. It answered
                        // the same question the filter sheet one tap to its right answers, from a
                        // narrower list, and two controls that do one job make the reader choose
                        // between them. Sorting stays where it was complete (the sheet, section
                        // "Sort Order"); the vacancy is a measurement instead: one tap ranks the
                        // library and connects to the route that wins.
                        MarbleButton(
                            label = if (repo.probeActive) trx("Stop") else trx("Fastest"),
                            glyph = if (repo.probeActive) MarbleGlyph.STOP else MarbleGlyph.SPARK,
                            style = MarbleButtonStyle.TINTED,
                            compact = true,
                            enabled = !repo.busy,
                            onClick = {
                                if (repo.probeActive || repo.probeCancelling) {
                                    repo.cancelProbes()
                                } else {
                                    repo.smart { best -> onConnect(best) }
                                }
                            }
                        )
                        MarbleIconButton(
                            glyph = MarbleGlyph.FILTER,
                            tint = if (filterChips.isNotEmpty()) colors.primary else colors.onSurfaceVariant,
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

                // V244 — the sources rail. One tap chooses a library; the sheet behind the ⋯
                // manages it. This replaces the chip strip that used to hide behind a filter
                // sheet three taps away from the question "which subscription am I looking at?".
                if (allProfiles.isNotEmpty()) {
                    item(key = "servers-sources") {
                        ServersLibraryRail(
                            cards = sourceCards,
                            selectedId = repo.librarySourceFilter,
                            onSelect = { id -> repo.selectLibrarySource(id) },
                            onManage = { librarySheetOpen = true }
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
                                MarbleButton(
                                    label = trx("Add Server or Subscription"),
                                    glyph = MarbleGlyph.PLUS,
                                    onClick = onOpenAddSheet,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MarbleButton(
                                        label = strings.pasteShortcut,
                                        glyph = MarbleGlyph.COPY,
                                        style = MarbleButtonStyle.TINTED,
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
                    val pendingDeleteId = deleteTarget?.id ?: pendingRemoval?.profileId.orEmpty()
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
                        if (!isCollapsed || !showGroupHeaders) {
                            items(
                                items = bucket.profiles,
                                key = { profile -> profile.id }
                            ) { profile ->
                                val country = repo.serverLocation(profile)
                                ServerRowItem(
                                    profile = profile,
                                    countryCode = country.code.takeIf { country.isKnown },
                                    countryName = country.name.takeIf { country.isKnown }.orEmpty(),
                                    active = repo.isActiveProfile(profile),
                                    selected = repo.isSelectedProfile(profile),
                                    result = benchmarks[profile.id],
                                    probeState = repo.probeStateOf(profile.id),
                                    swipeEnabled = !repo.probeActive &&
                                        !repo.probeCancelling &&
                                        selection.isEmpty(),
                                    pendingDelete = pendingDeleteId == profile.id,
                                    selectionMode = selection.isNotEmpty(),
                                    checked = profile.id in selection,
                                    onClick = { selectOrConnect(profile) },
                                    onMore = { actionTarget = profile },
                                    onLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selection = ServersSelection.toggle(selection, profile.id)
                                    },
                                    onSwipeDelete = { scheduleRemoval(profile) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // V244 — the multi-select roster floats at the BOTTOM now. At the top it sat on the
        // search line (the one control a long-press never needs), and the thumb that started a
        // selection is already at the bottom of the screen.
        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = MarbleTheme.bottomClearance + 8.dp
                )
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
            onDismiss = { filterSheetOpen = false }
        )
    }

    // V244 — the sources sheet: one place to see every library as a library (name, node count,
    // plan usage, measured flags) instead of a row of chips inside the filter sheet.
    if (librarySheetOpen) {
        ServersLibrarySheet(
            cards = sourceCards,
            selectedId = repo.librarySourceFilter,
            busy = repo.busy,
            scanning = repo.serverLocationScanning,
            located = repo.serverLocationSummary().first,
            total = allProfiles.size,
            onSelect = { id ->
                repo.selectLibrarySource(id)
                librarySheetOpen = false
            },
            onManageSubscription = { sub ->
                librarySheetOpen = false
                editSubscription = sub
                editSubName = sub.name
                editSubUrl = sub.url
            },
            onRefreshAll = { if (!repo.busy) repo.refreshAll() },
            onScanFlags = { repo.ensureServerLocations(force = true) },
            onAdd = {
                librarySheetOpen = false
                onOpenAddSheet()
            },
            onDismiss = { librarySheetOpen = false }
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
 * MARBLE_SERVERS_ATLAS_V244 — the live route is a strip, not a card with a button.
 *
 * V243 drew the active tunnel as a card carrying five facts and a full-width Disconnect: the
 * loudest object on a page whose job is choosing the *next* server, and a second stop control
 * competing with the Home pill. The strip keeps the three facts the reader actually needs —
 * which route, where it is, and how fast it answers — plus one 48dp stop target, in the height
 * of a single row.
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
    val country = repo.serverLocation(profile)
    val tone = if (connected) colors.success else colors.warning
    val health = when {
        !connected -> ServerHealth.UNTESTED
        livePingMs <= 0 -> ServerHealth.UNTESTED
        else -> serverHealthOf(livePingMs.toDouble(), true)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(tone.copy(alpha = 0.11f))
            .clickable(role = Role.Button, onClick = onInspect)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ServerFlagTile(
            code = country.code.takeIf { country.isKnown },
            profile = profile,
            width = 34.dp,
            height = 26.dp
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MarblePulseDot(color = tone, modifier = Modifier.size(7.dp))
                Text(
                    text = if (connected) trx("Active route") else trx("Connecting"),
                    color = tone,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = ServersAtlas.rowSubtitle(
                        profile = profile,
                        countryName = country.name.takeIf { country.isKnown }.orEmpty()
                    ),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = cleanServerName(profile.name).ifBlank { strings.node },
                color = colors.onSurface,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MarbleHealthPill(
            health = health,
            text = if (livePingMs > 0) faDigits("$livePingMs ms") else strings.pingIdleValue
        )
        MarbleIconButton(
            glyph = MarbleGlyph.STOP,
            tint = colors.error,
            contentDescription = strings.disconnect,
            onClick = onDisconnect
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
 * MARBLE_SERVERS_ATLAS_V244 — a section label, not a card header.
 *
 * V243's header was a 40dp line of labelSmall text with a 14dp chevron: it read as a divider,
 * not as the thing that owns the eighteen rows beneath it. V244 gives it the measured flag when
 * the section is a country, the name at title size, the count as a badge, freshness as a quiet
 * tail, and one 44dp overflow — inside a 48dp touch target, so folding a section is no longer a
 * precision gesture.
 */
@Composable
internal fun ServerGroupHeader(
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
    val stateLabel = if (collapsed) trx("Collapsed") else trx("Expanded")

    // MARBLE_EXPRESSIVE_BENTO_V250 — the overflow lives in a reserved trailing column.
    // Title, count and freshness share one flexible leading block, so a long subscription
    // name or a fresh timestamp can never push the three dots out of line with the rows.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server-group-header-${bucket.id}")
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onToggleCollapse)
            .semantics { this.stateDescription = stateLabel }
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarbleGlyphIcon(
                glyph = if (collapsed) MarbleGlyph.CHEVRON else MarbleGlyph.CHEVRON_DOWN,
                color = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            if (bucket.countryCode.isNotBlank()) {
                CountryFlagCircle(
                    code = bucket.countryCode,
                    size = ServersAtlas.SECTION_FLAG_SIZE_DP.dp
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = bucket.title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MarbleBadge(
                        text = faDigits("${bucket.profiles.size}"),
                        color = if (sub != null) colors.secondary else colors.tertiary
                    )
                    if (freshness != null) {
                        Text(
                            text = freshness,
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
        MarbleActionColumn(tag = "server-group-action-${bucket.id}") {
            Box {
                MarbleIconButton(
                    glyph = MarbleGlyph.MORE,
                    tint = colors.onSurfaceVariant,
                    contentDescription = "${trx("Group actions")}: ${bucket.title}",
                    onClick = { onMenuOpenChange(true) },
                    modifier = Modifier.testTag("server-group-more-${bucket.id}")
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
}

/**
 * MARBLE_SERVERS_ATLAS_V244 — one "add" affordance, in the header, with its verb on it.
 *
 * V243 shipped two of them: a `+` icon beside the title and an extended FAB over the last row,
 * plus a third button in the empty state. The pill is the only add on a populated page, and the
 * empty state keeps a full-width one because that page has nothing else to tap.
 */
@Composable
private fun AddServerPill(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MarbleGlyphIcon(
            glyph = MarbleGlyph.PLUS,
            color = colors.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
            weight = 1.2f
        )
        Text(
            text = trx("Add"),
            color = colors.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

/** The six orderings of the page, named once so the menu and the sheet cannot drift apart. */
@Composable
private fun serverSortChoices(): List<Pair<NodeSortMode, String>> = listOf(
    NodeSortMode.DEFAULT to trx("Subscription order"),
    NodeSortMode.PING to trx("Lowest ping"),
    NodeSortMode.SCORE to trx("Best score"),
    NodeSortMode.NAME to trx("Name (A–Z)"),
    NodeSortMode.COUNTRY to trx("Country"),
    NodeSortMode.PROTOCOL to trx("Protocol")
)

/**
 * MARBLE_SERVERS_ATLAS_V244 — the sources rail.
 *
 * The rail answers "which library am I in?" as a picture: name, measured flag cluster and node
 * count per card. In aggregate view only the management entry remains: section headers already
 * identify the sources. Choosing a library restores the switching rail without duplicating scan
 * counters from the management sheet.
 */
@Composable
private fun ServersLibraryRail(
    cards: List<ServersLibraryCard>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val railState = rememberLazyListState()
    LaunchedEffect(selectedId, cards.size) {
        val index = cards.indexOfFirst { it.id == selectedId }
        if (index > 0) railState.animateScrollToItem(index)
    }

    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarbleGlyphIcon(
                glyph = MarbleGlyph.LAYERS,
                color = colors.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = trx("Sources"),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            MarbleIconButton(
                glyph = MarbleGlyph.MENU,
                tint = colors.onSurfaceVariant,
                contentDescription = trx("Manage sources"),
                onClick = onManage
            )
        }
        if (selectedId != "all") LazyRow(
            state = railState,
            horizontalArrangement = Arrangement.spacedBy(ServersAtlas.LIBRARY_RAIL_GAP_DP.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(items = cards, key = { card -> card.id }) { card ->
                LibraryCardTile(
                    card = card,
                    selected = card.id == selectedId,
                    onClick = { onSelect(card.id) }
                )
            }
        }
    }
}

/**
 * One source card: name and count on top, the country cluster and the plan bar below.
 * Selected is a tone change plus a check — never an outline, per the V241 rule.
 */
@Composable
private fun LibraryCardTile(
    card: ServersLibraryCard,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val container = if (selected) colors.primaryContainer else colors.surfaceContainerLow
    val titleColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    val metaColor = if (selected) {
        colors.onPrimaryContainer.copy(alpha = 0.78f)
    } else {
        colors.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .width(ServersAtlas.LIBRARY_CARD_WIDTH_DP.dp)
            .height(ServersAtlas.LIBRARY_CARD_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(role = Role.RadioButton) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = card.title,
                modifier = Modifier.weight(1f),
                color = titleColor,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.CHECK,
                    color = colors.primary,
                    modifier = Modifier.size(15.dp),
                    weight = 1.2f
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlagCluster(
                codes = card.flags,
                size = ServersAtlas.LIBRARY_FLAG_SIZE_DP.dp,
                overlap = ServersAtlas.LIBRARY_FLAG_OVERLAP_DP.dp,
                ring = container
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = faDigits("${card.count}"),
                color = metaColor,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(titleColor.copy(alpha = 0.10f))
        ) {
            if (card.usageFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(card.usageFraction)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.primary else colors.secondary)
                )
            }
        }
    }
}

/**
 * The measured flags of a library, stacked like a signature. An unknown country is not drawn:
 * the cluster shows only what the sweep actually answered, and a globe stands in when it
 * answered nothing.
 */
@Composable
private fun FlagCluster(
    codes: List<String>,
    size: Dp = ServersAtlas.LIBRARY_FLAG_SIZE_DP.dp,
    overlap: Dp = ServersAtlas.LIBRARY_FLAG_OVERLAP_DP.dp,
    ring: Color? = null
) {
    val colors = MaterialTheme.colorScheme
    if (codes.isEmpty()) {
        Box(
            modifier = Modifier
                .height(size)
                .width(size),
            contentAlignment = Alignment.Center
        ) {
            MarbleGlyphIcon(
                glyph = MarbleGlyph.GLOBE,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(size * 0.72f)
            )
        }
        return
    }
    Box(
        modifier = Modifier
            .height(size)
            .width(size + overlap * (codes.size - 1).toFloat())
    ) {
        codes.forEachIndexed { index, code ->
            CountryFlagCircle(
                code = code,
                size = size,
                ring = ring,
                modifier = Modifier.offset(x = overlap * index.toFloat())
            )
        }
    }
}

/**
 * MARBLE_SERVERS_ATLAS_V244 — the leading identity of a row.
 *
 * A measured country is a real flag, drawn by the app's own vector art. A server whose location
 * is still unknown is not a grey circle: the tile shows the protocol monogram in the protocol's
 * tone, so the leading column always says something true. This is also why the row no longer
 * spends 38dp on a second protocol chip — the tile is the identity, and the subtitle explains it.
 */
@Composable
private fun ServerFlagTile(
    code: String?,
    profile: ProxyProfile,
    modifier: Modifier = Modifier,
    width: Dp = ServersAtlas.ROW_FLAG_WIDTH_DP.dp,
    height: Dp = ServersAtlas.ROW_FLAG_HEIGHT_DP.dp
) {
    val colors = MaterialTheme.colorScheme
    val tone = protocolRowColor(profile.scheme)
    val supported = CountryFlagSupported(code)
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(ServersAtlas.ROW_FLAG_CORNER_DP.dp))
            .background(
                if (supported) colors.surfaceContainerHighest else tone.copy(alpha = 0.16f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (supported) {
            Canvas(Modifier.matchParentSize()) {
                drawCountryFlag(code!!)
            }
        } else {
            Text(
                text = protocolChip(profile),
                color = tone,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                maxLines = 1
            )
        }
    }
}

/** One library's quiet second line: how many servers, and what the plan is doing. */
@Composable
private fun libraryCardSubtitle(card: ServersLibraryCard): String {
    val strings = Tr.now
    val parts = mutableListOf("${faDigits("${card.count}")} ${strings.serversUnit}")
    card.subscription?.let { sub ->
        when {
            sub.totalBytes > 0L ->
                parts += "${formatBytes(sub.uploadBytes + sub.downloadBytes)} / " +
                    formatBytes(sub.totalBytes)

            sub.updatedAt > 0L -> parts += trx(relativeTimeLabel(sub.updatedAt))
        }
    }
    return parts.joinToString(" · ")
}

/**
 * MARBLE_SERVERS_ATLAS_V244 — the sources sheet.
 *
 * Choosing a library used to be: open the filter sheet, find a scrolling chip strip, tap, dismiss.
 * The sheet lists every library as a card with its node count, its plan and its flags, puts the
 * two library-wide actions (refresh every source, re-scan every flag) at the top, and gives each
 * subscription one pencil to edit it — the same actions the old "This group" section carried,
 * without the four-tap round trip.
 */
@Composable
private fun ServersLibrarySheet(
    cards: List<ServersLibraryCard>,
    selectedId: String,
    busy: Boolean,
    scanning: Boolean,
    located: Int,
    total: Int,
    onSelect: (String) -> Unit,
    onManageSubscription: (Subscription) -> Unit,
    onRefreshAll: () -> Unit,
    onScanFlags: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    MarbleBottomSheet(
        title = trx("Sources"),
        subtitle = trx("Pick a library, refresh it, or scan its flags"),
        onDismiss = onDismiss,
        scrollable = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarbleButton(
                label = trx("Refresh all"),
                glyph = MarbleGlyph.REFRESH,
                style = MarbleButtonStyle.TINTED,
                compact = true,
                enabled = !busy,
                onClick = onRefreshAll,
                modifier = Modifier.weight(1f)
            )
            MarbleButton(
                label = trx("Scan flags"),
                glyph = MarbleGlyph.RADAR,
                style = MarbleButtonStyle.TINTED,
                compact = true,
                enabled = !scanning,
                onClick = onScanFlags,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = if (scanning) {
                "${trx("Scanning flags")} · ${faDigits("$located/$total")}"
            } else {
                "${faDigits("$located")} / ${faDigits("$total")} ${trx("located")}"
            },
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.forEach { card ->
                SourceLibraryCard(
                    card = card,
                    selected = card.id == selectedId,
                    onSelect = { onSelect(card.id) },
                    onManage = onManageSubscription
                )
            }
        }

        MarbleButton(
            label = trx("Add Server or Subscription"),
            glyph = MarbleGlyph.PLUS,
            style = MarbleButtonStyle.SECONDARY,
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * MARBLE_EXPRESSIVE_BENTO_V250 — one subscription card in the sources sheet.
 *
 * The selected check is a badge on the leading flag cluster, and the trailing action column
 * is reserved even for "All sources" and the manual bucket. Long names, quota text and the
 * selected state therefore cannot shift the three-dot menu from one card to the next.
 */
@Composable
internal fun SourceLibraryCard(
    card: ServersLibraryCard,
    selected: Boolean,
    onSelect: () -> Unit,
    onManage: (Subscription) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val container = if (selected) colors.primaryContainer else colors.surfaceContainerLow
    val titleColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    val metaColor = if (selected) colors.onPrimaryContainer.copy(alpha = 0.78f) else colors.onSurfaceVariant
    val subtitle = libraryCardSubtitle(card)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server-source-${card.id}")
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .clickable(role = Role.RadioButton) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect()
            }
            .semantics { this.selected = selected }
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(width = 56.dp, height = 36.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            FlagCluster(
                codes = card.flags,
                size = ServersAtlas.LIBRARY_FLAG_SIZE_DP.dp,
                overlap = ServersAtlas.LIBRARY_FLAG_OVERLAP_DP.dp,
                ring = container
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.CHECK,
                        color = colors.onPrimary,
                        modifier = Modifier.size(12.dp),
                        weight = 1.25f
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = card.title,
                color = titleColor,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = metaColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MarbleActionColumn(tag = "server-source-action-${card.id}") {
            val sub = card.subscription
            if (sub != null) {
                Box {
                    MarbleIconButton(
                        glyph = MarbleGlyph.MORE,
                        tint = colors.onSurfaceVariant,
                        contentDescription = "${trx("Manage subscription")}: ${card.title}",
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag("server-source-more-${card.id}")
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = colors.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text(trx("Edit subscription"), style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                MarbleGlyphIcon(
                                    glyph = MarbleGlyph.PENCIL,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onManage(sub)
                            }
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
    countryName: String = "",
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

    // MARBLE_PLAIN_VOICE_V246 — the "Delete" reveal lives *behind* the row, and the report saw it
    // through a selected row's translucent fill, right behind the ping column and the ⋯. It is now
    // drawn only while the row is actually being swiped. Read in the draw phase, so a drag never
    // recomposes the row.
    val revealAlpha = remember(dismissState) {
        derivedStateOf { ServersRowSurface.revealAlpha(dismissState.progress) }
    }

    // MARBLE_CLEAN_ROW_V249 — and it is not merely invisible at rest, it is *absent*. A selected
    // row is translucent, which makes it a window onto whatever is behind it, and a layer at alpha
    // zero is still a layer. This boolean flips twice per drag (going in, coming back), which is
    // the whole recomposition budget the gate costs.
    val revealComposed = remember(dismissState) {
        derivedStateOf { ServersRowSurface.revealComposed(dismissState.progress) }
    }

    // Read in the composition: a semantics lambda is not a composable context.
    val revealDescription = trx("Delete")

    // The swipe only OPENS the delete: the row slides back if the user changes their mind, and
    // the row leaves with its own profile when the undo window starts.
    LaunchedEffect(pendingDelete, profile.id) {
        if (!pendingDelete && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        // V244 — the row is its own rounded slab now, so the destructive reveal is clipped to
        // the same radius instead of showing square corners through it.
        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
        backgroundContent = {
            // V241/V243: the destructive reveal is a neutral tonal step with error ink.
            // MARBLE_CLEAN_ROW_V249: it is a glyph, not a sentence. The word that used to sit here
            // was the thing the report kept seeing through a selected row, and a trash can on a
            // destructive wash never needed a caption. Its name is still spoken, by semantics.
            if (revealComposed.value) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceContainerHighest)
                        .graphicsLayer { alpha = revealAlpha.value }
                        .semantics { contentDescription = revealDescription }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.TRASH,
                        color = colors.error,
                        modifier = Modifier.size(22.dp)
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
        key(profile.id) {
            ServerRowContent(
                profile = profile,
                countryCode = countryCode,
                countryName = countryName,
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
}

/**
 * MARBLE_SERVERS_ATLAS_V244 — the row carries a place, not a protocol badge.
 *
 * | zone | content |
 * |---|---|
 * | start | the measured flag (or the protocol monogram when the location sweep has not answered), or the selection check |
 * | middle | name (bold) + "Germany · TLS · XHTTP" — the country, then how it carries traffic; never the address |
 * | ping | reserved 88dp: four semantic bars + the number, or a skeleton while measuring |
 * | end | one 48dp overflow target |
 *
 * The 38dp protocol chip is gone: a row can afford one leading identity, and "which country is
 * this?" is the question a server list is asked far more often than "which wire scheme?" — the
 * protocol is answered by the tile on a server with no measured location, and it still has its
 * own filter in the filter sheet. There is no Delete control here by design: the row is a
 * server, and the delete lives behind the swipe (with an undo) or the overflow sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ServerRowContent(
    profile: ProxyProfile,
    countryCode: String?,
    countryName: String = "",
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
    // The country is measured once by the repository sweep and named here; the transport line
    // follows it. A server with no answer yet leads with its protocol instead of lying.
    val subtitle = ServersAtlas.rowSubtitle(profile = profile, countryName = countryName)
    val stateLabel = when {
        active -> trx("Active")
        selected -> trx("Selected")
        checked -> trx("Checked")
        else -> ""
    }
    // One tonal slab per row: quiet at rest, green-washed when the tunnel is on it, secondary
    // when it is the route the Home pill will use, and primary-tinted when it is selected.
    val container = when {
        active -> colors.successContainer
        selected || checked -> colors.secondaryContainer.copy(alpha = 0.38f)
        else -> colors.surfaceContainerLow
    }
    val accent = when {
        active -> colors.onSuccessContainer
        selected || checked -> colors.primary
        else -> colors.onSurface
    }
    // Read in the composition: a semantics lambda is not a composable context.
    val spokenPing = pingBadgeText(result, probeState)
    val spokenAddress = "\u2068${profile.host}\u2069".takeIf { profile.host.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server-row-${profile.id}")
            .heightIn(min = ServersDeck.ROW_MIN_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(20.dp))
            // MARBLE_CLEAN_ROW_V249 — the selection and activity tints stay translucent so the page
            // can breathe behind them, but they are laid over an opaque base of the page's own
            // surface first. A row is never a window again: whatever the swipe reveal, an undo
            // animation or a future layer puts behind it, the row shows its own two colours.
            .background(colors.surface)
            .background(container)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            )
            .semantics {
                this.selected = if (selectionMode) checked else selected
                stateDescription = stateLabel
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
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
            Box(contentAlignment = Alignment.Center) {
                ServerFlagTile(code = countryCode, profile = profile)

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

            }
            Text(
                text = if (active) "${trx("Active")} · $subtitle" else subtitle,
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
 * MARBLE_SERVERS_ATLAS_V244 — the sheet answers only what the page cannot show.
 *
 * The group chip strip moved to the sources rail and the "This group" action list moved to the
 * sources sheet, so this sheet has one job again: order, narrow and regroup. It also exposes the
 * latency ceiling the filter model always supported but the page never offered, and one row to
 * re-run the flag sweep.
 */
@Composable
private fun ServersFilterSheet(
    repo: AppRepository,
    protocolTallies: List<com.marbleng.app.core.ProtocolTally>,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val settings = repo.settings
    val narrowing = settings.serversProtocolFilter.isNotBlank() ||
        settings.serversOnlyReachable ||
        settings.serversMaxPingMs > ServersQuery.MAX_PING_OFF

    MarbleBottomSheet(
        title = trx("Sort & Filter"),
        subtitle = trx("Order the list, narrow it, or regroup it by country"),
        onDismiss = onDismiss,
        scrollable = true
    ) {
        if (narrowing) {
            MarbleButton(
                label = trx("Reset Filters"),
                glyph = MarbleGlyph.CLOSE,
                style = MarbleButtonStyle.TINTED,
                compact = true,
                onClick = {
                    repo.updateSettings(
                        settings.copy(
                            serversProtocolFilter = "",
                            serversOnlyReachable = false,
                            serversMaxPingMs = ServersQuery.MAX_PING_OFF
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        MarbleSectionHeader(title = trx("Sort Order"))
        MarbleCard {
            serverSortChoices().forEachIndexed { idx, (mode, label) ->
                if (idx > 0) MarbleDivider()
                MarbleChoiceRow(
                    title = label,
                    selected = settings.nodeSortMode == mode,
                    glyph = MarbleGlyph.TUNERS,
                    iconTint = colors.primary,
                    onClick = { repo.updateSettings(settings.copy(nodeSortMode = mode)) }
                )
            }
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
                            val next = if (
                                settings.serversProtocolFilter.equals(tally.scheme, ignoreCase = true)
                            ) {
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

        // V244 — the filter model always had a latency ceiling; the page never showed it.
        MarbleSectionHeader(title = trx("Latency ceiling"))
        MarbleChipStrip {
            ServersQuery.MAX_PING_CHOICES.forEach { choice ->
                MarblePillChip(
                    label = if (choice == ServersQuery.MAX_PING_OFF) {
                        trx("Off")
                    } else {
                        "${faDigits("$choice")} ms"
                    },
                    selected = settings.serversMaxPingMs == choice,
                    accent = colors.warning,
                    onClick = { repo.updateSettings(settings.copy(serversMaxPingMs = choice)) }
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

        MarbleSectionHeader(title = trx("Server flags"))
        MarbleCard {
            MarbleListItem(
                title = trx("Scan flags"),
                subtitle = trx("Measure the country of every server again"),
                glyph = MarbleGlyph.RADAR,
                iconTint = colors.primary,
                showChevron = false,
                enabled = !repo.serverLocationScanning,
                onClick = { repo.ensureServerLocations(force = true) }
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
