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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.IpAddressBook
import com.marbleng.app.model.IpAddressEntry
import com.marbleng.app.model.IpEgress
import com.marbleng.app.model.IpKind
import com.marbleng.app.model.IpReachability
import com.marbleng.app.model.IpScope
import com.marbleng.app.model.IpStackReport
import com.marbleng.app.model.dockSlotDefaultCaption
import com.marbleng.app.model.parseDockSlotKind
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.settleSpec
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

/**
 * MARBLE_IP_IDENTITY_V251 — the fourth tab, in every state.
 *
 * The tab used to be a user-owned shortcut. It is now one page with one job: draw every address this
 * device holds, every address the internet sees, the difference between the two, and whether each
 * family answers — before the tunnel is up, while it is up, and after it comes down. The arithmetic
 * is [IpAddressBook]; this file is the drawing.
 *
 * The design language is the same Material 3 Expressive the rest of the app speaks, pushed one
 * degree further because this page is an *instrument*: a large address card that morphs between the
 * tunnel state and the direct state, an animated orbit of the two families, tiltable pressure
 * gauges for the witnesses, and a soft grain of motion that stops the moment the platform or the
 * user asks for calm.
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
    val motion = marbleMotion()
    val listState = rememberLazyListState()
    val report = repo.ipStackReport
    val scanning = repo.ipStackScanning
    val connected = repo.state == "CONNECTED"
    val connecting = repo.state == "CONNECTING" || repo.state == "DISCONNECTING"

    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    // The page sweeps itself on arrival, in every state. A tab that answers "what address am I on"
    // cannot wait for a tap, and it must not depend on a tunnel being up to have an answer.
    LaunchedEffect(Unit) {
        repo.refreshIpStack()
    }

    val slotTitle = trx(dockSlotDefaultCaption(parseDockSlotKind(repo.settings.dockSlotKind), ""))
    val copyAllLabel = trx("Copy every address")
    // Resolved here, not inside the copy callbacks: those lambdas are not composable scopes.
    val copiedNotice = strings.ipCopied
    val readingLabel = trx("Reading…")

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // No opaque page fill: the shell's ambient light is the page's ground, so a tab change
            // does not repaint the backdrop in a second tone.
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
                    else -> trx("Every address this phone holds, and where it goes")
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
                IpExitHero(
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

            item(key = "ip-stack") {
                IpStackStrip(report = report)
            }

            item(key = "ip-families") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MarbleSectionHeader(
                        title = trx("Address families"),
                        badge = faDigits(
                            IpKind.entries.count { kind ->
                                report.reachability.any { it.kind == kind && it.ok }
                            }.toString()
                        ),
                        badgeColor = colors.success
                    )
                    MarbleCard(contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            IpFamilyGauge(
                                kind = IpKind.IPV4,
                                reachable = report.reachableV4,
                                entries = report.ipv4Addresses,
                                publicAddress = report.publicV4,
                                tint = colors.primary
                            )
                            IpFamilyGauge(
                                kind = IpKind.IPV6,
                                reachable = report.reachableV6,
                                entries = report.ipv6Addresses,
                                publicAddress = report.publicV6,
                                tint = colors.tertiary
                            )
                        }
                    }
                }
            }

            item(key = "ip-addresses") {
                IpAddressLedger(
                    report = report,
                    onCopy = { literal ->
                        clipboard.setText(AnnotatedString(literal))
                        repo.setRuntimeMessage(copiedNotice)
                    }
                )
            }

            item(key = "ip-dns") {
                IpDnsCard(
                    report = report,
                    onOpenDnsSettings = { onOpenSettingsPage(SettingsDestination.DNS_FAMILY) }
                )
            }

            item(key = "ip-verdict") {
                IpVerdictCard(
                    report = report,
                    connected = connected,
                    onCopyAll = {
                        val all = buildString {
                            report.exit?.address?.takeIf { it.isNotBlank() }?.let {
                                appendLine("exit: $it")
                            }
                            report.addresses.forEach { entry ->
                                appendLine("${entry.interfaceName}: ${entry.cidr} (${entry.scope.id})")
                            }
                        }.trim()
                        if (all.isNotBlank()) {
                            clipboard.setText(AnnotatedString(all))
                            repo.setRuntimeMessage(copiedNotice)
                        }
                    },
                    copyLabel = copyAllLabel
                )
            }

            item(key = "ip-tools") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSectionHeader(title = trx("Tools"))
                    MarbleCard {
                        MarbleListItem(
                            title = trx("Measure the address families"),
                            subtitle = trx("Scan which of IPv4 and IPv6 this network really carries"),
                            glyph = MarbleGlyph.RADAR,
                            iconTint = colors.secondary,
                            enabled = !repo.ipFamilyScanning && !repo.busy,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                repo.scanIpFamilyForProfiles(repo.libraryProfiles, "library")
                            }
                        )
                        MarbleDivider()
                        MarbleListItem(
                            title = trx("DNS and IP version"),
                            subtitle = "${report.dnsServers.firstOrNull() ?: repo.settings.dnsPrimaryIp} · " +
                                trx("resolver and family policy"),
                            glyph = MarbleGlyph.GLOBE,
                            iconTint = colors.tertiary,
                            onClick = { onOpenSettingsPage(SettingsDestination.DNS_FAMILY) }
                        )
                        MarbleDivider()
                        MarbleListItem(
                            title = trx("Privacy and leak audit"),
                            subtitle = trx("Compare the tunnel address with the network underneath"),
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.success,
                            onClick = onOpenDiagnostics
                        )
                        MarbleDivider()
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

            if (repo.ipStackError.isNotBlank()) {
                item(key = "ip-error") {
                    MarbleSectionFooter(text = trx(repo.ipStackError))
                }
            }

            item(key = "ip-footer") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = trx("Addresses are read from this device and from one public echo service."),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
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
// The hero: one address, in whichever state the phone is in.
// ---------------------------------------------------------------------------------------------

/**
 * The exit card. It is the page's headline, so it is the loudest surface: a tonal wash in the
 * accent of the current state, a slow breathing orbit behind the literal, and an
 * [AnimatedContent] that slides the address in and out so a refresh never replaces text in place.
 */
@Composable
private fun IpExitHero(
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
    // Resolved in the composition: the button labels live inside clickable scopes below.
    val refreshLabel = trx("Refresh")
    val copyLabel = trx("Copy")
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
    val literal = exit?.address.orEmpty()
    val detail = IpAddressBook.egressDetail(exit)
    val scope = if (literal.isNotBlank()) IpAddressBook.scopeOf(literal) else IpScope.OTHER

    val orbit = rememberInfiniteTransition(label = "ipOrbit")
    val orbitAngle by orbit.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 9_000)),
        label = "ipOrbitAngle"
    )
    val breath by orbit.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ipBreath"
    )
    val stillness = if (motion.loops) 1f else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Flat tone first, gradient second: a translucent gradient over the page surface
            // keeps the card opaque in every theme, including the true-black one where a
            // transparent wash would show the window behind it.
            .clip(RoundedCornerShape(32.dp))
            .background(colors.surfaceContainerLow)
            .background(
                Brush.linearGradient(
                    listOf(
                        tone.copy(alpha = 0.20f),
                        colors.surfaceContainerLow.copy(alpha = 0.02f),
                        tone.copy(alpha = 0.08f)
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
        // The orbit: a hairline ring and one travelling bead, drawn only while motion is allowed.
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(132.dp)
                .graphicsLayer { alpha = 0.9f * stillness }
        ) {
            val radius = size.minDimension * 0.44f * breath
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = tone.copy(alpha = 0.16f),
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f)
            )
            drawCircle(
                color = tone.copy(alpha = 0.30f),
                radius = radius * 0.62f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
            val angle = Math.toRadians(orbitAngle.toDouble())
            drawCircle(
                color = tone.copy(alpha = 0.75f),
                radius = 4.5f,
                center = Offset(
                    center.x + (radius * kotlin.math.cos(angle)).toFloat(),
                    center.y + (radius * kotlin.math.sin(angle)).toFloat()
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(tone.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = if (connected) MarbleGlyph.SHIELD else MarbleGlyph.GLOBE,
                        color = tone,
                        modifier = Modifier.size(16.dp),
                        weight = 1.1f
                    )
                }
                Text(
                    text = label,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                if (scanning) {
                    MarblePulseDot(color = tone)
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
                    MarbleBadge(
                        text = if (scope == IpScope.PUBLIC) trx("public") else scope.id.replace('_', ' '),
                        color = if (scope == IpScope.PUBLIC) colors.success else colors.warning
                    )
                    if (exit?.viaTunnel == true) {
                        MarbleBadge(text = trx("through the tunnel"), color = colors.primary)
                    } else {
                        MarbleBadge(text = trx("direct"), color = colors.secondary)
                    }
                }
            }

            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (literal.isBlank()) {
                Text(
                    text = trx("Tap refresh to ask a public echo service for this device's address."),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
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
                    onClick = { if (literal.isNotBlank()) onCopy(literal) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The stack strip: transport, families, MTU — one line of chips that animate their width.
// ---------------------------------------------------------------------------------------------

@Composable
private fun IpStackStrip(report: IpStackReport) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IpStackChip(
            label = report.transport.ifBlank { "?" }.uppercase(),
            glyph = if (report.transport.contains("wifi", true)) MarbleGlyph.SPARK else MarbleGlyph.RADAR,
            tint = colors.primary
        )
        IpStackChip(
            label = when {
                report.hasIpv4 && report.hasIpv6 -> "DUAL"
                report.hasIpv6 -> "IPv6"
                report.hasIpv4 -> "IPv4"
                else -> "—"
            },
            glyph = MarbleGlyph.LAYERS,
            tint = if (report.hasIpv4 && report.hasIpv6) colors.success else colors.warning
        )
        if (report.mtu > 0) {
            IpStackChip(label = "MTU ${report.mtu}", glyph = MarbleGlyph.GAUGE, tint = colors.secondary)
        }
        if (report.tunnelActive) {
            IpStackChip(label = trx("tunnel"), glyph = MarbleGlyph.SHIELD, tint = colors.success)
        }
        if (report.metered) {
            IpStackChip(label = trx("metered"), glyph = MarbleGlyph.BELL, tint = colors.warning)
        }
    }
}

@Composable
private fun IpStackChip(label: String, glyph: MarbleGlyph, tint: Color) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        MarbleGlyphIcon(glyph = glyph, color = tint, modifier = Modifier.size(13.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The family gauge: a witness dial plus the addresses it applies to.
// ---------------------------------------------------------------------------------------------

@Composable
private fun IpFamilyGauge(
    kind: IpKind,
    reachable: IpReachability?,
    entries: List<IpAddressEntry>,
    publicAddress: IpEgress?,
    tint: Color
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val ok = reachable?.ok == true
    val target = when {
        reachable == null -> 0f
        ok -> 1f
        else -> 0.28f
    }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (motion.still) snap() else motion.panSpec(720),
        label = "ipFamilyProgress"
    )
    val ink = when {
        reachable == null -> colors.onSurfaceVariant
        ok -> colors.success
        else -> colors.warning
    }
    val advertised = entries.any { it.scope != IpScope.LINK_LOCAL && it.scope != IpScope.UNSPECIFIED }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainerLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val radius = (size.minDimension - stroke) / 2f
                drawCircle(
                    color = ink.copy(alpha = 0.16f),
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
                drawArc(
                    color = ink,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.height - stroke
                    )
                )
            }
            Text(
                text = when {
                    reachable == null -> "…"
                    ok -> faDigits("${reachable.latencyMs}")
                    else -> "—"
                },
                color = ink,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = kind.label,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1
                )
                MarbleBadge(
                    text = when {
                        ok -> trx("reached")
                        reachable == null -> trx("not measured")
                        else -> trx("no answer")
                    },
                    color = ink
                )
            }
            Text(
                text = when {
                    publicAddress?.discovered == true -> publicAddress.address
                    reachable?.ok == true -> "${reachable.label} · ${reachable.target}"
                    reachable != null -> reachable.detail
                    advertised -> trx("The interface reports an address · not measured yet")
                    else -> trx("This network does not report one")
                },
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = faDigits("${entries.size}"),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The ledger: every address, grouped by whether it is lent by the tunnel or owned by the device.
// ---------------------------------------------------------------------------------------------

@Composable
private fun IpAddressLedger(
    report: IpStackReport,
    onCopy: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val landlines = report.underlayAddresses
    val tunnel = report.tunnelAddresses

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarbleSectionHeader(
            title = trx("Addresses on this device"),
            badge = faDigits("${report.addresses.size}"),
            badgeColor = colors.primary
        )
        MarbleCard {
            if (landlines.isEmpty()) {
                MarbleListItem(
                    title = trx("No address on the physical network"),
                    subtitle = trx("Wi-Fi and mobile data both reported nothing"),
                    glyph = MarbleGlyph.GLOBE,
                    iconTint = colors.warning,
                    showChevron = false
                )
            } else {
                landlines.forEachIndexed { index, entry ->
                    if (index > 0) MarbleDivider()
                    IpAddressRow(entry = entry, onCopy = onCopy)
                }
            }
            if (tunnel.isNotEmpty()) {
                MarbleDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.SHIELD,
                        color = colors.success,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = trx("Lent by the tunnel"),
                        color = colors.success,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                tunnel.forEachIndexed { index, entry ->
                    if (index > 0) MarbleDivider()
                    IpAddressRow(entry = entry, onCopy = onCopy)
                }
            }
        }
    }
}

@Composable
private fun IpAddressRow(
    entry: IpAddressEntry,
    onCopy: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tone = when (entry.scope) {
        IpScope.PUBLIC -> colors.success
        IpScope.PRIVATE, IpScope.UNIQUE_LOCAL, IpScope.CGNAT, IpScope.NAT64 -> colors.warning
        IpScope.LOOPBACK -> colors.onSurfaceVariant
        else -> colors.secondary
    }
    MarbleListItem(
        title = IpAddressBook.shorten(entry.address),
        subtitle = buildString {
            append(entry.interfaceName.ifBlank { trx("unknown interface") })
            if (entry.prefixLength >= 0) {
                append(" · ")
                append(IpAddressBook.networkOf(entry))
            }
        },
        leading = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(tone.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (entry.kind == IpKind.IPV6) "6" else "4",
                    color = tone,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        trailing = {
            MarbleBadge(
                text = trx(entry.scope.id.replace('_', ' ')),
                color = tone
            )
        },
        showChevron = false,
        onClick = { onCopy(IpAddressBook.shorten(entry.address)) }
    )
}

// ---------------------------------------------------------------------------------------------
// DNS, verdict, skeletons
// ---------------------------------------------------------------------------------------------

@Composable
private fun IpDnsCard(
    report: IpStackReport,
    onOpenDnsSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val servers = report.dnsServers.filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarbleSectionHeader(
            title = trx("Resolvers"),
            badge = faDigits("${servers.size}"),
            badgeColor = colors.tertiary
        )
        MarbleCard {
            if (servers.isEmpty()) {
                MarbleListItem(
                    title = trx("No resolver reported"),
                    subtitle = trx("Android did not publish the resolver list for this network"),
                    glyph = MarbleGlyph.GLOBE,
                    iconTint = colors.warning,
                    showChevron = false
                )
            } else {
                servers.forEachIndexed { index, server ->
                    if (index > 0) MarbleDivider()
                    MarbleListItem(
                        title = server,
                        subtitle = trx("Resolver handed out by this network"),
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = colors.tertiary,
                        showChevron = false
                    )
                }
            }
            MarbleDivider()
            MarbleListItem(
                title = trx("DNS and IP version"),
                subtitle = trx("Choose the resolver and which family is preferred"),
                glyph = MarbleGlyph.TUNERS,
                iconTint = colors.primary,
                onClick = onOpenDnsSettings
            )
        }
    }
}

@Composable
private fun IpVerdictCard(
    report: IpStackReport,
    connected: Boolean,
    onCopyAll: () -> Unit,
    copyLabel: String
) {
    val colors = MaterialTheme.colorScheme
    val rows = listOf(
        Triple(
            trx("Network"),
            IpAddressBook.networkLine(report),
            MarbleGlyph.RADAR
        ),
        Triple(
            trx("Families"),
            IpAddressBook.familyVerdict(report),
            MarbleGlyph.LAYERS
        ),
        Triple(
            trx("Privacy"),
            IpAddressBook.leakLine(report),
            if (connected) MarbleGlyph.SHIELD else MarbleGlyph.EYE
        ),
        Triple(
            trx("Addresses"),
            report.addressLine,
            MarbleGlyph.GLOBE
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarbleSectionHeader(title = trx("What this means"))
        MarbleCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEachIndexed { index, (label, value, glyph) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MarbleRowIcon(
                            glyph = glyph,
                            tint = when (index) {
                                0 -> colors.primary
                                1 -> colors.tertiary
                                2 -> if (connected) colors.success else colors.warning
                                else -> colors.secondary
                            },
                            size = 34.dp,
                            glyphSize = 18.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                color = colors.onSurface,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = value,
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (index < rows.lastIndex) {
                        MarbleDivider(insetStart = 46.dp)
                    }
                }
                MarbleButton(
                    label = copyLabel,
                    glyph = MarbleGlyph.COPY,
                    style = MarbleButtonStyle.SECONDARY,
                    compact = true,
                    onClick = onCopyAll,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun IpSkeletons() {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(colors.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IpSkeletonLine(widthFraction = 0.4f, height = 12)
                Spacer(Modifier.height(6.dp))
                IpSkeletonLine(widthFraction = 0.8f, height = 28)
                IpSkeletonLine(widthFraction = 0.55f, height = 12)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceContainerLow)
        )
        MarbleCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                IpSkeletonLine(widthFraction = 1f, height = 58)
                IpSkeletonLine(widthFraction = 1f, height = 58)
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
