package com.marbleng.app.ui

// MARBLE_HOME_V137
//
// Home rebuilt around the VPN connect experience. The page is one calm vertical rhythm with a
// fixed hierarchy:
//
//   1. [HomeShortcutRowV137]   QR scanner / copy-paste import / ping the current server.
//   2. [HomeStatusBannerV137]  the connection banner: READY / CONNECTING / CONNECTED /
//      DISCONNECTING with the server, IP, ping, uptime and protocol in one place.
//   3. [HomeServerDeckV137]    the selected-server selector, synced with the Servers group:
//      the same selection state, so a group switch here updates the Servers page and back.
//   4. the main connection control (the Settings-chosen silhouette, owned by MarbleHomeStyles /
//      MarbleHomeStudio — this file never duplicates a button).
//   5. [HomeLivePingPanelV137] live ping of ONLY the currently connecting/connected server,
//      measured async, expanding in with a fade the moment the route comes up.
//
// The large lower SERVER / SOURCE / IP / UPTIME / PING cards are gone: their facts moved upward
// into the banner and the ping panel, so nothing is rendered twice and the page breathes.
// Everything here is presentation over the shared [HomeEvidence] model plus the repository's
// single source of truth for group, server and connection state — no widget invents a fact.

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.ServerlessFreedomEngine
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.core.ServersFilter
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.ProxyProfile

private val V137CardShape = RoundedCornerShape(20.dp)

/** The three words a failed Home ping may show, resolved against the active language. */
@Composable
internal fun pingFailureLabel(failure: String): String {
    val t = Tr.now
    return when (failure.trim().lowercase()) {
        "timeout" -> t.pingTimeout
        "unreachable" -> t.pingUnreachable
        else -> t.pingFailedShort
    }
}

// ---------------------------------------------------------------------------------------------
// 1. Shortcut row — QR / paste / ping, always on top
// ---------------------------------------------------------------------------------------------

/**
 * The top shortcut row: QR scanner, copy-paste import, and the ping of the current server.
 * Compact icon-and-label targets with the shared press physics; the ping shortcut carries the
 * live value of the V137 ping channel (tunnel while connected, endpoint otherwise).
 */
@Composable
internal fun HomeShortcutRowV137(
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val pingTappable = homePingTappable(evidence)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeShortcutCellV137(
            icon = HomeGlyph.QR,
            label = t.qrShortcut,
            tone = tone,
            description = t.qrShortcut,
            onClick = actions.onQrImport,
            modifier = Modifier.weight(1f)
        )
        HomeShortcutCellV137(
            icon = HomeGlyph.PASTE,
            label = t.pasteShortcut,
            tone = tone,
            description = t.pasteShortcut,
            onClick = actions.onPasteImport,
            modifier = Modifier.weight(1f)
        )
        HomeShortcutCellV137(
            icon = HomeGlyph.PULSE,
            label = homePingLabel(evidence),
            sublabel = homePingActionHint(evidence).ifBlank { t.connectionPing },
            tone = homePingTone(evidence, tone),
            description = "${t.connectionPing}: ${homePingLabel(evidence)}",
            enabled = pingTappable,
            onClick = actions.onTestPing,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeShortcutCellV137(
    icon: HomeGlyph,
    label: String,
    tone: Color,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String = "",
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .72f))
            .background(tone.copy(alpha = .07f))
            .border(1.dp, tone.copy(alpha = .20f), shape)
            .kineticClickable(
                enabled = enabled,
                role = Role.Button,
                pressScale = .95f,
                boundedShape = shape,
                onClick = onClick
            )
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HomeGlyphIcon(icon, tone.copy(alpha = if (enabled) 1f else .45f), Modifier.size(22.dp))
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (fadeIn(MarbleMotionSpecs.QuickReveal) + scaleIn(initialScale = .94f, animationSpec = MarbleMotionSpecs.QuickReveal)) togetherWith
                    (fadeOut(MarbleMotionSpecs.QuickExit) + scaleOut(targetScale = .96f, animationSpec = MarbleMotionSpecs.QuickExit))
            },
            label = "shortcut-label"
        ) { value ->
            Text(
                value,
                color = Aether.Ink.copy(alpha = if (enabled) 1f else .5f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    fontSize = 12.sp
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (sublabel.isNotBlank()) {
            Text(
                sublabel,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Status banner — the connection, stated once
// ---------------------------------------------------------------------------------------------

/**
 * The connection banner: READY / CONNECTING / CONNECTED / DISCONNECTING with the server, the
 * IP, the ping, the uptime and the protocol consolidated into one card. State changes crossfade
 * with a gentle scale on the response spring; the uptime ticks only while a session runs and the
 * ping value animates between its states. Tapping toggles the connection, exactly like the main
 * control — the banner is the status, not a second button with second behaviour.
 */
@Composable
internal fun HomeStatusBannerV137(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val motion = MarbleMotion.current
    val statusKey = when {
        evidence.connected -> "connected"
        evidence.connecting -> "connecting"
        evidence.disconnecting -> "disconnecting"
        evidence.blocked -> "blocked"
        else -> "ready"
    }
    val statusWord = when {
        evidence.connected -> t.statusProtected
        evidence.connecting -> t.securingRoute
        evidence.disconnecting -> t.disconnecting
        evidence.blocked -> t.reset
        else -> t.readyToConnect
    }
    val uptime = rememberUptimeLabel(evidence.connectedSinceMs)
    val protocol = evidence.profile?.scheme?.trim()?.uppercase()?.ifBlank { null }
    // The connecting/disconnecting dot breathes; every other state holds still.
    val dotPulse = if (evidence.connecting || evidence.disconnecting) motion.breathe(1_200) else 0f
    val armed = !evidence.disconnecting

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(V137CardShape)
            .background(Aether.VoidElevated.copy(alpha = .78f))
            .background(tone.copy(alpha = .08f))
            .border(1.2.dp, tone.copy(alpha = .30f), V137CardShape)
            .kineticClickable(
                enabled = armed,
                role = Role.Button,
                pressScale = .985f,
                boundedShape = V137CardShape,
                onClick = onToggle
            )
            .semantics { contentDescription = "$statusWord: ${evidence.nodeName.ifBlank { t.chooseRoute }}" }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Canvas(Modifier.size(12.dp)) {
                drawCircle(
                    color = tone.copy(alpha = .55f + .45f * dotPulse),
                    radius = size.minDimension / 2f * (1f + .12f * dotPulse)
                )
            }
            AnimatedContent(
                targetState = statusWord,
                transitionSpec = {
                    (fadeIn(MarbleMotionSpecs.ResponseFloat) + scaleIn(initialScale = .96f, animationSpec = MarbleMotionSpecs.ResponseFloat)) togetherWith
                        (fadeOut(MarbleMotionSpecs.ExitFloat) + scaleOut(targetScale = .98f, animationSpec = MarbleMotionSpecs.ExitFloat))
                },
                label = "banner-status"
            ) { word ->
                Text(
                    word.uppercase(),
                    color = tone,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            if (protocol != null) {
                Text(
                    protocol,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        AnimatedContent(
            targetState = evidence.nodeName.ifBlank { t.chooseRoute },
            transitionSpec = {
                fadeIn(MarbleMotionSpecs.ResponseFloat) togetherWith fadeOut(MarbleMotionSpecs.ExitFloat)
            },
            label = "banner-server"
        ) { name ->
            Text(
                "${evidence.flag} $name".trim(),
                color = Aether.Ink,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // The consolidated facts: IP, ping, uptime. Each keeps a fixed slot so the banner never
        // reflows between states; the ping value animates like the shortcut above.
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            BannerFactV137(
                label = t.ipAddress,
                value = evidence.ip.ifBlank { t.unavailable },
                tone = Aether.InkMuted,
                modifier = Modifier.weight(1.4f)
            )
            BannerFactV137(
                label = t.connectionPing,
                value = homePingLabel(evidence),
                tone = homePingTone(evidence, Aether.InkMuted),
                animated = true,
                modifier = Modifier.weight(1f)
            )
            BannerFactV137(
                label = t.uptime,
                value = if (evidence.connected) uptime else "—",
                tone = Aether.InkMuted,
                tabular = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BannerFactV137(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    tabular: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label.uppercase(),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = .8.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (animated) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    fadeIn(MarbleMotionSpecs.QuickReveal) togetherWith fadeOut(MarbleMotionSpecs.QuickExit)
                },
                label = "banner-fact"
            ) { v ->
                Text(
                    v,
                    color = tone,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = if (tabular) "tnum" else null
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                value,
                color = tone,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = if (tabular) "tnum" else null
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Server deck — the selector, synced with the Servers group
// ---------------------------------------------------------------------------------------------

/**
 * The selected-server selector, synced with the Servers group: it renders the *same* group
 * ([AppRepository.librarySourceFilter]) through the *same* [ServersQuery] filter and sort the
 * Servers page uses, so switching the group here updates the Servers page and switching it
 * there updates Home — one selection state, two presentations.
 *
 * Tapping a server selects it ([AppRepository.selectProfile]); the connect button acts on the
 * selection immediately because selection and the persisted last-route reference are the same
 * key. The active (traffic-carrying) row is marked Connected, the selected-but-idle row
 * Selected; both survive recomposition, rotation and process restart through the repository.
 */
@Composable
internal fun HomeServerDeckV137(
    repo: AppRepository,
    actions: HomeActions,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val settings = repo.settings
    val benchmarks = remember(repo.benchmarks) { repo.benchmarks.associateBy { it.profileId } }
    // The exact Servers-page filter, minus the free-text query (Home is a selector, not a
    // browser) and minus country bucketing (Home shows the group flat).
    val filter = remember(
        settings.serversProtocolFilter,
        repo.librarySourceFilter,
        settings.serversOnlyReachable,
        settings.serversMaxPingMs
    ) {
        ServersFilter(
            query = "",
            protocol = settings.serversProtocolFilter,
            sourceId = repo.librarySourceFilter,
            onlyReachable = settings.serversOnlyReachable,
            maxPingMs = settings.serversMaxPingMs,
            groupByCountry = false
        )
    }
    val servers = remember(repo.libraryProfiles, filter, benchmarks, settings.nodeSortMode, settings.nodeSortReverse) {
        ServersQuery.sort(
            profiles = ServersQuery.visible(repo.libraryProfiles, filter, benchmarks),
            mode = settings.nodeSortMode,
            reverse = settings.nodeSortReverse,
            benchmarks = benchmarks
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(V137CardShape)
            .background(Aether.VoidElevated.copy(alpha = .72f))
            .border(1.dp, tone.copy(alpha = .20f), V137CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeGroupSwitcherV137(
            repo = repo,
            tone = tone,
            serverCount = servers.size
        )
        if (servers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    t.homeNoServers,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                MarbleInfoPillV137(t.homeOpenServers, tone, actions.onLibrary)
            }
        } else {
            val listState = rememberLazyListState()
            // Keep the selected server in view when the deck first opens on it.
            val selectedIndex = remember(servers, repo.selectedProfileId, repo.selectedProfileSourceId) {
                servers.indexOfFirst {
                    it.id == repo.selectedProfileId &&
                        (repo.selectedProfileSourceId.isBlank() || it.subscriptionId == repo.selectedProfileSourceId)
                }.coerceAtLeast(0)
            }
            LaunchedEffect(selectedIndex) {
                if (selectedIndex > 0) listState.scrollToItem(selectedIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 288.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(servers, key = { "${it.subscriptionId}:${it.id}" }) { profile ->
                    val active = repo.isActiveProfile(profile)
                    val selected = repo.isSelectedProfile(profile)
                    HomeServerRowV137(
                        profile = profile,
                        active = active,
                        selected = selected,
                        measuredMs = ServersQuery.measuredMs(profile, benchmarks),
                        failed = ServersQuery.hasFailedMeasurement(profile, benchmarks),
                        testing = repo.probeStateOf(profile.id) != com.marbleng.app.model.ProbeState.IDLE,
                        tone = tone,
                        onSelect = { repo.selectProfile(profile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeGroupSwitcherV137(
    repo: AppRepository,
    tone: Color,
    serverCount: Int
) {
    val t = Tr.now
    var menuOpen by remember { mutableStateOf(false) }
    val currentName = when (repo.librarySourceFilter) {
        "all" -> t.homeAllGroups
        "manual" -> "Manual"
        ServerlessFreedomEngine.SOURCE_ID -> "Marble Freedom"
        else -> repo.subscriptions.firstOrNull { it.id == repo.librarySourceFilter }?.name
            ?: t.homeAllGroups
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t.homeCurrentGroup.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = .8.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$currentName • $serverCount",
                color = Aether.Ink,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(tone.copy(alpha = .12f))
                    .border(1.dp, tone.copy(alpha = .30f), RoundedCornerShape(12.dp))
                    .kineticClickable(
                        role = Role.Button,
                        pressScale = .96f,
                        boundedShape = RoundedCornerShape(12.dp),
                        onClick = { menuOpen = true }
                    )
                    .semantics { contentDescription = t.homeCurrentGroup }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HomeGlyphIcon(HomeGlyph.LIBRARY, tone, Modifier.size(16.dp))
                Text(
                    t.changeRoute,
                    color = tone,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false
                )
                HomeChevronV137(tone)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                HomeGroupMenuEntryV137(
                    name = t.homeAllGroups,
                    count = repo.libraryProfiles.size,
                    selected = repo.librarySourceFilter == "all",
                    tone = tone,
                    onClick = {
                        menuOpen = false
                        repo.selectLibrarySource("all")
                    }
                )
                repo.subscriptions.forEach { sub ->
                    val count = repo.libraryProfiles.count { it.subscriptionId == sub.id }
                    HomeGroupMenuEntryV137(
                        name = sub.name,
                        count = count,
                        selected = repo.librarySourceFilter == sub.id,
                        tone = tone,
                        onClick = {
                            menuOpen = false
                            repo.selectLibrarySource(sub.id)
                        }
                    )
                }
                val manualCount = repo.libraryProfiles.count { it.subscriptionId == "manual" }
                HomeGroupMenuEntryV137(
                    name = "Manual",
                    count = manualCount,
                    selected = repo.librarySourceFilter == "manual",
                    tone = tone,
                    onClick = {
                        menuOpen = false
                        repo.selectLibrarySource("manual")
                    }
                )
                if (!repo.libraryFreedomHidden) {
                    val freedomCount = repo.libraryProfiles.count {
                        it.subscriptionId == ServerlessFreedomEngine.SOURCE_ID
                    }
                    HomeGroupMenuEntryV137(
                        name = "Marble Freedom",
                        count = freedomCount,
                        selected = repo.librarySourceFilter == ServerlessFreedomEngine.SOURCE_ID,
                        tone = tone,
                        onClick = {
                            menuOpen = false
                            repo.selectLibrarySource(ServerlessFreedomEngine.SOURCE_ID)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeGroupMenuEntryV137(
    name: String,
    count: Int,
    selected: Boolean,
    tone: Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    name,
                    color = if (selected) tone else Aether.Ink,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$count",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
                )
            }
        },
        trailingIcon = { if (selected) HomeGlyphIcon(HomeGlyph.CHECK, tone, Modifier.size(16.dp)) },
        onClick = onClick
    )
}

@Composable
private fun HomeServerRowV137(
    profile: ProxyProfile,
    active: Boolean,
    selected: Boolean,
    measuredMs: Int,
    failed: Boolean,
    testing: Boolean,
    tone: Color,
    onSelect: () -> Unit
) {
    val t = Tr.now
    val rowTone = when {
        active -> Aether.Emerald
        selected -> tone
        else -> Aether.InkMuted
    }
    val shape = RoundedCornerShape(14.dp)
    val country = remember(profile.name, profile.host) { ServersQuery.countryOf(profile) }
    val flag = country.flag.ifBlank { leadingFlagGlyph(profile.name).orEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    active -> Aether.Emerald.copy(alpha = .10f)
                    selected -> tone.copy(alpha = .10f)
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    active -> Aether.Emerald.copy(alpha = .45f)
                    selected -> tone.copy(alpha = .45f)
                    else -> Aether.InkFaint.copy(alpha = .25f)
                },
                shape
            )
            .kineticClickable(
                role = Role.Button,
                pressScale = .985f,
                boundedShape = shape,
                onClick = onSelect
            )
            .semantics {
                contentDescription = buildString {
                    append(stripLeadingFlag(profile.name))
                    if (active) append(", ${t.homeConnectedBadge}")
                    else if (selected) append(", ${t.homeSelectedBadge}")
                }
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            flag.ifBlank { "◌" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                stripLeadingFlag(profile.name),
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                ServersQuery.badge(profile),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Status chip: Connected > Selected > measured ping > failed/testing/idle.
        when {
            active -> HomeStatusChipV137(t.homeConnectedBadge, Aether.Emerald)
            selected -> HomeStatusChipV137(t.homeSelectedBadge, tone)
            testing -> HomeStatusChipV137(t.pingChecking, Aether.InkMuted)
            measuredMs > 0 -> Text(
                "$measuredMs ms",
                color = marbleMetricTone(pingMetricBand(measuredMs)),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1,
                softWrap = false
            )
            failed -> Text(
                "✕",
                color = Aether.Danger,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            else -> Text(
                "—",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (active || selected) {
            HomeGlyphIcon(
                if (active) HomeGlyph.CHECK else HomeGlyph.PULSE,
                rowTone,
                Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun HomeStatusChipV137(label: String, tone: Color) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        label,
        color = tone,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(shape)
            .background(tone.copy(alpha = .14f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun MarbleInfoPillV137(label: String, tone: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        color = tone,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(shape)
            .background(tone.copy(alpha = .12f))
            .border(1.dp, tone.copy(alpha = .35f), shape)
            .kineticClickable(
                role = Role.Button,
                pressScale = .96f,
                boundedShape = shape,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// 5. Live ping panel — only the connecting/connected server, measured async
// ---------------------------------------------------------------------------------------------

/**
 * The live ping panel: it exists only while a route is coming up or carrying traffic, expands
 * in with a fade, and measures ONLY the server the tunnel is attached to — never the library.
 * The value animates between Checking → milliseconds → Timeout / Unreachable / Failed, with the
 * colour following the latency band, so a glance states both the number and its meaning.
 */
@Composable
internal fun HomeLivePingPanelV137(
    evidence: HomeEvidence,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val motion = MarbleMotion.current
    val live = evidence.connecting || evidence.connected
    val (ms, state, failure) = homeV137PingChannel(evidence)
    val pulse = if (state == ConnectionPingState.MEASURING || evidence.connecting) motion.breathe(1_200) else 0f
    val valueTone = when (state) {
        ConnectionPingState.MEASURED -> if (ms >= 20) marbleMetricTone(pingMetricBand(ms)) else Aether.Danger
        ConnectionPingState.FAILED -> Aether.Danger
        else -> tone
    }

    AnimatedVisibility(
        visible = live,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(MarbleMotionSpecs.ResponseFloat) + expandVertically(
            animationSpec = MarbleMotionSpecs.Layout,
            expandFrom = Alignment.Top
        ),
        exit = fadeOut(MarbleMotionSpecs.ExitFloat) + shrinkVertically(
            animationSpec = MarbleMotionSpecs.Layout,
            shrinkTowards = Alignment.Top
        ),
        label = "live-ping-panel"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(V137CardShape)
                .background(Aether.VoidElevated.copy(alpha = .72f))
                .background(tone.copy(alpha = .06f))
                .border(1.dp, tone.copy(alpha = .22f), V137CardShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Canvas(Modifier.size(9.dp)) {
                    drawCircle(
                        color = valueTone.copy(alpha = .6f + .4f * pulse),
                        radius = size.minDimension / 2f * (1f + .15f * pulse)
                    )
                }
                Text(
                    t.livePing.uppercase(),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stripLeadingFlag(evidence.nodeName).ifBlank { t.chooseRoute },
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                    textAlign = TextAlign.End
                )
            }
            AnimatedContent(
                targetState = when (state) {
                    ConnectionPingState.MEASURING -> "checking"
                    ConnectionPingState.MEASURED -> if (ms >= 20) "ms:$ms" else "failed"
                    ConnectionPingState.FAILED -> "failed:$failure"
                    ConnectionPingState.IDLE -> if (evidence.connecting) "checking" else "idle"
                },
                transitionSpec = {
                    (fadeIn(MarbleMotionSpecs.ResponseFloat) + scaleIn(initialScale = .96f, animationSpec = MarbleMotionSpecs.ResponseFloat)) togetherWith
                        (fadeOut(MarbleMotionSpecs.ExitFloat) + scaleOut(targetScale = .98f, animationSpec = MarbleMotionSpecs.ExitFloat))
                },
                label = "live-ping-value"
            ) { key ->
                when {
                    key == "checking" -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            t.pingChecking,
                            color = valueTone,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Canvas(Modifier.size(14.dp)) {
                            val r = size.minDimension / 2f
                            drawCircle(
                                color = valueTone.copy(alpha = .25f),
                                radius = r,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = valueTone.copy(alpha = .35f + .55f * pulse),
                                radius = r * (.35f + .35f * pulse)
                            )
                        }
                    }
                    key.startsWith("ms:") -> Text(
                        "$ms ms",
                        color = valueTone,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    key.startsWith("failed") -> Text(
                        pingFailureLabel(failure.ifBlank { "error" }),
                        color = Aether.Danger,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    else -> Text(
                        t.livePingWaiting,
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                t.livePingHint,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared chevron used by the group switcher affordance
// ---------------------------------------------------------------------------------------------

@Composable
internal fun HomeChevronV137(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp, 10.dp)) {
        val stroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawPath(
            path = Path().apply {
                moveTo(size.width * .12f, size.height * .22f)
                lineTo(size.width * .5f, size.height * .72f)
                lineTo(size.width * .88f, size.height * .22f)
            },
            color = color,
            style = stroke
        )
    }
}
