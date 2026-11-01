@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.IpAddressBook
import com.marbleng.app.model.IpAddressEntry
import com.marbleng.app.model.IpEgress
import com.marbleng.app.model.IpKind
import com.marbleng.app.model.IpScope
import com.marbleng.app.model.IpStackReport
import com.marbleng.app.model.dockSlotDefaultCaption
import com.marbleng.app.model.parseDockSlotKind
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.panSpec
import com.marbleng.app.ui.theme.settleSpec
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

/**
 * MARBLE_IP_PASSPORT_V253 — the fourth tab, redrawn as a passport page.
 *
 * The V251 page was an instrument: gauges, orbits, per-interface ledgers and verdict essays. None
 * of that answered the question a person opens this tab with. This page does, in the order the
 * question is asked:
 *
 * 1. **the flag** — the country the internet sees this phone in, drawn large by the app's own
 *    vector flag art, with the public address under it and one tap to copy;
 * 2. **the facts a leak-check site shows** — country, region, city, provider, ASN, time zone,
 *    network and resolvers, each only when it was actually measured ([browserleaks.com/ip]'s set,
 *    minus the ones a phone cannot answer);
 * 3. **this device** — the handful of local addresses worth naming, not the whole kernel table;
 * 4. **one privacy verdict** — is the real address hidden or not, and the two doors that act on it.
 *
 * The arithmetic behind every value is still [IpAddressBook]; the sweep is still
 * `repo.refreshIpStack()`, run on arrival in every state. This file is only the drawing — and the
 * drawing is deliberately calm: one hero, three quiet cards, and nothing that moves unless it has
 * something new to say. The text scale ships at its smallest rung
 * (`TextScale.DEFAULT_PERCENT`), so a fresh install opens on the quietest possible page.
 */
@Composable
fun IpIdentityScreen(
    repo: AppRepository,
    onOpenDiagnostics: () -> Unit,
    onOpenSettingsPage: (SettingsDestination) -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val report = repo.ipStackReport
    val scanning = repo.ipStackScanning
    val connected = repo.state == "CONNECTED"
    val connecting = repo.state == "CONNECTING" || repo.state == "DISCONNECTING"

    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    // The page answers "what address am I on" the moment it opens — before the tunnel is up,
    // while it is up, and after it comes down. A tab that waits for a tap is a tab that is wrong.
    LaunchedEffect(Unit) {
        repo.refreshIpStack()
    }

    val slotTitle = trx(dockSlotDefaultCaption(parseDockSlotKind(repo.settings.dockSlotKind), ""))
    // Resolved here, not inside the copy callbacks: those lambdas are not composable scopes.
    val copiedNotice = strings.ipCopied

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // No opaque page fill: the shell's ambient light is the page's ground.
            .statusBarsPadding()
            .testTag("ip-identity-page"),
        contentPadding = PaddingValues(
            start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            top = 8.dp,
            bottom = MarbleTheme.bottomClearance
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "ip-header") {
            MarbleLargeHeader(
                title = slotTitle,
                subtitle = when {
                    scanning -> trx("Reading every address…")
                    report == null -> trx("Your address, in every state")
                    else -> trx("Where the internet sees you")
                },
                collapseFraction = collapseFractionOf(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
                )
            ) {
                MarbleIconButton(
                    glyph = if (scanning) MarbleGlyph.STOP else MarbleGlyph.REFRESH,
                    tint = if (scanning) colors.warning else colors.primary,
                    contentDescription = trx("Refresh every address"),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        if (!scanning) repo.refreshIpStack(force = true)
                    }
                )
                MarbleIconButton(
                    glyph = MarbleGlyph.TUNERS,
                    tint = colors.secondary,
                    contentDescription = trx("Network diagnostics"),
                    onClick = onOpenDiagnostics
                )
            }
        }

        if (scanning && report == null) {
            item(key = "ip-skeleton") {
                IpSkeletons()
            }
        }

        if (report != null) {
            item(key = "ip-exit-hero") {
                IpPassportHero(
                    report = report,
                    connected = connected,
                    connecting = connecting,
                    scanning = scanning,
                    onRefresh = { repo.refreshIpStack(force = true) },
                    onCopy = { literal ->
                        clipboard.setText(AnnotatedString(literal))
                        repo.setRuntimeMessage(copiedNotice)
                    }
                )
            }

            item(key = "ip-internet") {
                IpInternetCard(
                    report = report,
                    onCopy = { literal ->
                        clipboard.setText(AnnotatedString(literal))
                        repo.setRuntimeMessage(copiedNotice)
                    }
                )
            }

            item(key = "ip-device") {
                IpDeviceCard(
                    report = report,
                    onCopy = { literal ->
                        clipboard.setText(AnnotatedString(literal))
                        repo.setRuntimeMessage(copiedNotice)
                    }
                )
            }

            item(key = "ip-privacy") {
                IpPrivacyCard(
                    report = report,
                    onOpenDiagnostics = onOpenDiagnostics,
                    onOpenSettingsPage = onOpenSettingsPage
                )
            }

            if (repo.ipStackError.isNotBlank()) {
                item(key = "ip-error") {
                    MarbleSectionFooter(text = trx(repo.ipStackError))
                }
            }

            item(key = "ip-footer") {
                Text(
                    text = trx("Addresses are read from this device and from one public echo service."),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (!scanning) {
            item(key = "ip-empty") {
                MarbleEmptyState(
                    title = trx("No address yet"),
                    body = trx("This device has not reported an address. Turn on Wi-Fi or mobile data, then refresh."),
                    glyph = MarbleGlyph.GLOBE,
                    tint = colors.tertiary
                ) {
                    MarbleButton(
                        label = strings.refreshIp,
                        glyph = MarbleGlyph.REFRESH,
                        onClick = { repo.refreshIpStack(force = true) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The hero: one flag, one address — the page's only loud surface.
// ---------------------------------------------------------------------------------------------

/**
 * The passport hero. The flag is the headline (drawn large, by [drawCountryFlag]), the address the
 * internet sees is the sub-head, and the two facts a person repeats on a support call — where and
 * through what — sit under them as pills. A refresh slides the literal in and out; nothing else on
 * the page animates.
 */
@Composable
private fun IpPassportHero(
    report: IpStackReport,
    connected: Boolean,
    connecting: Boolean,
    scanning: Boolean,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val haptics = LocalHapticFeedback.current
    val exit = report.exit
    val literal = exit?.address.orEmpty()
    val scope = if (literal.isNotBlank()) IpAddressBook.scopeOf(literal) else IpScope.OTHER
    val tone = when {
        connecting -> colors.warning
        connected && report.tunnelActive -> colors.success
        else -> colors.primary
    }
    val label = when {
        connecting -> trx("Moving the route")
        connected && report.tunnelActive -> trx("Address the internet sees")
        else -> trx("Your address right now")
    }
    // Resolved in the composition: the button labels live inside clickable scopes below.
    val refreshLabel = trx("Refresh")
    val readingLabel = trx("Reading…")
    val copyLabel = trx("Copy")
    val place = listOf(exit?.city.orEmpty(), exit?.country.orEmpty())
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")

    val breathTransition = rememberInfiniteTransition(label = "ipHeroBreath")
    val breath by breathTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ipHeroBreathScale"
    )
    val stillness = if (motion.loops) breath else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colors.surfaceContainerLow)
            .background(
                Brush.linearGradient(
                    listOf(
                        tone.copy(alpha = 0.18f),
                        colors.surfaceContainerLow.copy(alpha = 0.02f),
                        tone.copy(alpha = 0.07f)
                    )
                )
            )
            .clickable(
                role = Role.Button,
                enabled = literal.isNotBlank()
            ) {
                if (literal.isNotBlank()) {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onCopy(literal)
                }
            }
            .testTag("ip-exit-hero")
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The passport face: the flag large, the place under it, the state pill beside it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IpCountryFlagPanel(
                    countryCode = exit?.countryCode.orEmpty(),
                    modifier = Modifier.size(width = 116.dp, height = 78.dp),
                    shadowTone = tone
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (place.isBlank()) trx("Not measured") else place,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (scanning) {
                            MarblePulseDot(color = tone)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (exit?.countryCode.orEmpty().isNotBlank()) {
                            MarbleBadge(
                                text = exit!!.countryCode.uppercase(),
                                color = tone
                            )
                        }
                        MarbleBadge(
                            text = if (exit?.family == IpKind.IPV6) "IPv6" else "IPv4",
                            color = colors.secondary
                        )
                    }
                    Text(
                        text = label,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedContent(
                targetState = literal,
                transitionSpec = {
                    (
                        slideInVertically(motion.panSpec(260)) { height -> height } +
                            fadeIn(motion.settleSpec(260))
                        ) togetherWith (
                        slideOutVertically(motion.panSpec(260)) { height -> -height } +
                            fadeOut(motion.settleSpec(260))
                        )
                },
                label = "ipExitLiteral"
            ) { shown ->
                Text(
                    text = if (shown.isBlank()) trx("Not measured") else shown,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ip-exit-literal")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (literal.isNotBlank()) {
                    if (scope == IpScope.PUBLIC) {
                        MarbleBadge(text = trx("public"), color = colors.success)
                    }
                    if (exit?.viaTunnel == true) {
                        MarbleBadge(text = trx("through the tunnel"), color = colors.primary)
                    } else {
                        MarbleBadge(text = trx("direct"), color = colors.secondary)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MarbleButton(
                    label = if (scanning) readingLabel else refreshLabel,
                    glyph = MarbleGlyph.REFRESH,
                    style = MarbleButtonStyle.TINTED,
                    compact = true,
                    enabled = !scanning,
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                )
                MarbleButton(
                    label = copyLabel,
                    glyph = MarbleGlyph.COPY,
                    style = MarbleButtonStyle.SECONDARY,
                    compact = true,
                    enabled = literal.isNotBlank(),
                    onClick = {
                        if (literal.isNotBlank()) {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onCopy(literal)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // One soft halo behind the flag while a sweep is running — motion that says something.
        if (scanning) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(96.dp)
                    .padding(8.dp)
            ) {
                drawCircle(
                    color = tone.copy(alpha = 0.12f * (stillness - 0.9f) * 10f),
                    radius = size.minDimension / 2f,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
        }
    }
}

/**
 * The flag panel: the country's flag at passport size, clipped to a soft rectangle with one low
 * shadow. Countries the art library does not carry fall back to a tonal globe, never to a wrong
 * flag.
 */
@Composable
private fun IpCountryFlagPanel(
    countryCode: String,
    modifier: Modifier = Modifier,
    shadowTone: Color = MaterialTheme.colorScheme.primary
) {
    val colors = MaterialTheme.colorScheme
    val supported = CountryFlagSupported(countryCode)
    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        shadowTone.copy(alpha = 0.16f),
                        colors.surfaceContainerHighest
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (supported) {
            Canvas(Modifier.fillMaxSize()) {
                drawCountryFlag(countryCode)
            }
        } else {
            MarbleGlyphIcon(
                glyph = MarbleGlyph.GLOBE,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The facts: what a leak-check site shows, each row only when it was measured.
// ---------------------------------------------------------------------------------------------

/**
 * The browserleaks.com/ip set: where the exit is, who carries it, and how this network names
 * itself. Every row is copyable and every row hides itself when the sweep has no answer — a blank
 * line is noise, not information.
 */
@Composable
private fun IpInternetCard(
    report: IpStackReport,
    onCopy: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val exit = report.exit
    val publicV4 = report.publicV4
    val publicV6 = report.publicV6

    val rows = buildList<Triple<String, String, IpFactVisual>> {
        publicV4?.let {
            add(Triple(trx("Public IPv4"), it.address, IpFactVisual(MarbleGlyph.GLOBE, colors.primary)))
        }
        publicV6?.let {
            add(Triple(trx("Public IPv6"), it.address, IpFactVisual(MarbleGlyph.GLOBE, colors.tertiary)))
        }
        exit?.country?.takeIf { it.isNotBlank() }?.let {
            add(Triple(trx("Country"), it, IpFactVisual(MarbleGlyph.ROUTE, colors.success)))
        }
        exit?.region?.takeIf { it.isNotBlank() }?.let {
            add(Triple(trx("Region"), it, IpFactVisual(MarbleGlyph.ROUTE, colors.secondary)))
        }
        exit?.city?.takeIf { it.isNotBlank() }?.let {
            add(Triple(trx("City"), it, IpFactVisual(MarbleGlyph.ROUTE, colors.secondary)))
        }
        exit?.organization?.takeIf { it.isNotBlank() }?.let {
            add(Triple(trx("Provider"), it, IpFactVisual(MarbleGlyph.SERVER, colors.warning)))
        }
        exit?.asn?.takeIf { it.isNotBlank() }?.let {
            add(Triple(trx("ASN"), it, IpFactVisual(MarbleGlyph.LAYERS, colors.warning)))
        }
        exit?.timezone?.takeIf { it.isNotBlank() }?.let {
            add(Triple(trx("Time zone"), it, IpFactVisual(MarbleGlyph.GAUGE, colors.onSurfaceVariant)))
        }
        if (report.transport.isNotBlank()) {
            add(
                Triple(
                    trx("Network"),
                    networkLabel(report),
                    IpFactVisual(MarbleGlyph.TUNNEL, colors.primary)
                )
            )
        }
        val resolvers = report.dnsServers.filter { it.isNotBlank() }
        if (resolvers.isNotEmpty()) {
            add(
                Triple(
                    trx("DNS"),
                    resolvers.joinToString(" · "),
                    IpFactVisual(MarbleGlyph.GLOBE, colors.tertiary)
                )
            )
        }
    }

    if (rows.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarbleSectionHeader(
            title = trx("Where the internet sees you"),
            badge = exit?.countryCode?.takeIf { it.isNotBlank() }?.uppercase().orEmpty(),
            badgeColor = colors.success
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (label, value, _) ->
                        IpFactRow(label, value, onCopy, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class IpFactVisual(val glyph: MarbleGlyph, val tint: Color)

/** One measured fact: the label small above, the value large below, one tap to copy. */
@Composable
private fun IpFactRow(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var copied by remember(value) { mutableStateOf(false) }
    var copyGeneration by remember(value) { mutableStateOf(0) }
    LaunchedEffect(copyGeneration) {
        if (copyGeneration > 0) { delay(1500); copied = false }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onCopy(value)
                copied = true
                copyGeneration++
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Text(
                text = value,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        MarbleGlyphIcon(
            glyph = if (copied) MarbleGlyph.CHECK else MarbleGlyph.COPY,
            color = if (copied) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun networkLabel(report: IpStackReport): String {
    val base = when {
        report.transport.contains("wifi", ignoreCase = true) -> trx("Wi-Fi")
        report.transport.contains("mobile", ignoreCase = true) ||
            report.transport.contains("cellular", ignoreCase = true) -> trx("Mobile data")
        report.transport.isNotBlank() -> report.transport
        else -> ""
    }
    return if (report.metered && base.isNotBlank()) "$base · " + trx("metered") else base
}

// ---------------------------------------------------------------------------------------------
// This device: the few local addresses worth naming.
// ---------------------------------------------------------------------------------------------

/**
 * The device half, trimmed to what a person can act on: the working IPv4 and IPv6 of the network
 * underneath, and the address the tunnel lends when one is up. Loopback, link-local, multicast
 * and the rest of the kernel's table are facts for `ip addr`, not for this page.
 */
@Composable
private fun IpDeviceCard(
    report: IpStackReport,
    onCopy: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interesting = report.addresses
        .filter { entry ->
            entry.scope != IpScope.LOOPBACK &&
                entry.scope != IpScope.UNSPECIFIED &&
                entry.scope != IpScope.MULTICAST &&
                entry.scope != IpScope.LINK_LOCAL &&
                entry.scope != IpScope.DOCUMENTATION
        }
        .take(IpAddressBook.MAX_LISTED_ADDRESSES.coerceAtMost(4))

    if (interesting.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarbleSectionHeader(
            title = trx("This device"),
            badge = faDigits("${interesting.size}"),
            badgeColor = colors.tertiary
        )
        MarbleCard {
            interesting.forEachIndexed { index, entry ->
                if (index > 0) MarbleDivider(insetStart = 50.dp)
                IpDeviceRow(entry = entry, onCopy = onCopy)
            }
        }
    }
}

@Composable
private fun IpDeviceRow(
    entry: IpAddressEntry,
    onCopy: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val tone = when {
        entry.scope == IpScope.PUBLIC -> colors.success
        entry.tunnel -> colors.primary
        entry.scope == IpScope.PRIVATE ||
            entry.scope == IpScope.UNIQUE_LOCAL ||
            entry.scope == IpScope.CGNAT -> colors.warning
        else -> colors.secondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onCopy(IpAddressBook.shorten(entry.address))
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tone.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (entry.kind == IpKind.IPV6) "6" else "4",
                color = tone,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = IpAddressBook.shorten(entry.address),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.interfaceName.ifBlank { trx("unknown interface") } +
                    " · " + trx(entry.scope.id.replace('_', ' ')),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (entry.tunnel) {
            MarbleBadge(text = trx("through the tunnel"), color = colors.primary)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One verdict: is the real address hidden, and the two doors that act on it.
// ---------------------------------------------------------------------------------------------

@Composable
private fun IpPrivacyCard(
    report: IpStackReport,
    onOpenDiagnostics: () -> Unit,
    onOpenSettingsPage: (SettingsDestination) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val exit = report.exit
    val direct = report.egress.firstOrNull { !it.viaTunnel && it.discovered }
    val hidden = report.tunnelActive &&
        exit != null &&
        exit.viaTunnel &&
        direct != null &&
        !exit.address.equals(direct.address, ignoreCase = true)
    val tone = if (hidden) colors.success else colors.warning

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarbleSectionHeader(title = trx("What this means"))
        MarbleCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MarbleRowIcon(
                    glyph = if (hidden) MarbleGlyph.SHIELD else MarbleGlyph.EYE,
                    tint = tone,
                    size = 40.dp,
                    glyphSize = 20.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hidden) {
                            trx("Your real IP is hidden")
                        } else {
                            trx("Your real IP is visible")
                        },
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = trx("Compare the tunnel address with the network underneath"),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MarbleDivider(insetStart = 56.dp)
            MarbleListItem(
                title = trx("Privacy and leak audit"),
                subtitle = trx("Compare the tunnel address with the network underneath"),
                glyph = MarbleGlyph.SHIELD,
                iconTint = colors.success,
                onClick = onOpenDiagnostics
            )
            MarbleDivider(insetStart = 50.dp)
            MarbleListItem(
                title = trx("Hide my real IP"),
                subtitle = trx("Protect your IP and find server countries"),
                glyph = MarbleGlyph.EYE_OFF,
                iconTint = colors.warning,
                onClick = { onOpenSettingsPage(SettingsDestination.IDENTITY_LOCATION) }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Skeletons
// ---------------------------------------------------------------------------------------------

@Composable
private fun IpSkeletons() {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(colors.surfaceContainerLow)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 116.dp, height = 78.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surfaceContainerHighest)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IpSkeletonLine(widthFraction = 0.7f, height = 16)
                    IpSkeletonLine(widthFraction = 0.4f, height = 12)
                }
            }
        }
        MarbleCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                IpSkeletonLine(widthFraction = 1f, height = 52)
                IpSkeletonLine(widthFraction = 1f, height = 52)
                IpSkeletonLine(widthFraction = 1f, height = 52)
            }
        }
    }
}

@Composable
private fun IpSkeletonLine(widthFraction: Float, height: Int) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val breath by rememberInfiniteTransition(label = "ipSkeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ipSkeletonAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (motion.loops) {
                    colors.onSurface.copy(alpha = breath * 0.16f)
                } else {
                    colors.onSurface.copy(alpha = 0.10f)
                }
            )
    )
}
