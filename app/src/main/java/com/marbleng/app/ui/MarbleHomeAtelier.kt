package com.marbleng.app.ui

// MARBLE_ROUTE_ATELIER_V207 — the fifth Home presentation: "Route".

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.ProxyProfile

private val AtelierCardShape = RoundedCornerShape(20.dp)

private val AtelierInsetShape = RoundedCornerShape(16.dp)

@Composable
internal fun HomeThemeAtelier(
    repo: AppRepository,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    val tone = marbleRouteTone(evidence.routeState)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 2.dp)
            .padding(bottom = bottomClearance + 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeTopActionBar(evidence, actions, repo)

        RouteConnectCard(
            evidence = evidence,
            actions = actions,
            nationalCause = repo.nationalEventCause
        )
        RouteSelectedServerCard(evidence = evidence, actions = actions)

        IosServerListBox(
            repo = repo,
            evidence = evidence,
            actions = actions,
            modifier = Modifier.fillMaxWidth(),
            maxListHeight = 340.dp
        )
        if (repo.settings.homeShowLiveQuality) {
            RouteQualityCard(repo = repo, evidence = evidence)
        }
        HomeSessionStats(evidence = evidence, actions = actions, tone = tone)
        HomeShortcutDeck(
            evidence = evidence,
            actions = actions,
            accent = tone,

            pingAction = evidence.pingChipAction
        )
    }
}

@Composable
private fun RouteConnectCard(
    evidence: HomeEvidence,
    actions: HomeActions,
    nationalCause: String = ""
) {
    val state = evidence.routeState

    val motion = MarbleMotion.current
    val tone by animateColorAsState(
        targetValue = marbleRouteTone(state),
        animationSpec = if (motion.animates()) {
            tween(MarbleConnectMotion.StateColorMs)
        } else {
            snap()
        },
        label = "atelier-route-tone"
    )
    val path = marbleRoutePathOf(
        state = state,
        egressResolved = evidence.ip.isNotBlank(),
        egressFailed = state == MarbleRouteState.BLOCKED
    )
    HomeCloudCard(modifier = Modifier.fillMaxWidth(), shape = AtelierCardShape) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = homeStatusText(evidence),

                    color = tone,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = atelierRouteLine(evidence, evidence.profile),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RoutePathSignet(path = path, tone = tone)

            PrismButton(
                label = homeActionLabel(evidence),
                onClick = { actions.onToggleConnection() },
                tone = tone,
                variant = when (state) {
                    MarbleRouteState.CONNECTED -> PrismButtonVariant.Danger
                    MarbleRouteState.BLOCKED -> PrismButtonVariant.Secondary
                    else -> PrismButtonVariant.Primary
                },
                detail = "",
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MarbleControlKind.Primary.minHeight)
            )
            val problem = atelierProblem(evidence, nationalCause)
            if (problem != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AtelierInsetShape)
                        .background(Aether.Danger.copy(alpha = .09f))
                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = problem,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    PrismIconButton(
                        onClick = { actions.onPingRoute() },
                        tone = Aether.Danger,
                        size = 30.dp,
                        descriptiveLabel = "Measure the route again"
                    ) {
                        HomeGlyphIcon(HomeGlyph.REFRESH, Aether.Danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun atelierRouteLine(evidence: HomeEvidence, profile: ProxyProfile?): String {
    val t = Tr.now
    return when {
        profile == null -> t.noServersBody
        evidence.ip.isNotBlank() -> listOfNotNull(
            evidence.ip.takeIf { it.isNotBlank() },
            evidence.location.takeIf { it.isNotBlank() && it != "—" }
        ).joinToString(" • ")
        else -> listOfNotNull(
            profile.scheme.uppercase().takeIf { it.isNotBlank() },
            profile.transport.uppercase().takeIf { it.isNotBlank() && it != "NONE" },
            evidence.location.takeIf { it.isNotBlank() && it != "—" }
                ?: trx("location not verified")
        ).joinToString(" • ").ifBlank { t.readyToConnect }
    }
}

@Composable
private fun atelierProblem(evidence: HomeEvidence, nationalCause: String): String? {
    val t = Tr.now
    return when {
        evidence.blocked -> evidence.pingFailure.ifBlank { t.connectionStopped }
        evidence.pingState == ConnectionPingState.FAILED && evidence.pingFailure.isNotBlank() ->
            pingFailureLabel(evidence.pingFailure)
        nationalCause.isNotBlank() ->
            "${trx("National filtering detected")} • ${trx("ranking frozen")} • ${nationalCause.replace("-", " ")}"
        evidence.ipError -> trx("The exit address could not be read. The tunnel is up; the reading service did not answer.")
        else -> null
    }
}

@Composable
private fun RoutePathSignet(
    path: MarbleRoutePath,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val stages = listOf(
        trx("Device") to path.device,
        trx("Tunnel") to path.tunnel,
        trx("Exit") to path.egress
    )

    val speech = atelierSignetSpeech(path)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AtelierInsetShape)
            .background(Aether.Glass.copy(alpha = .38f))
            .border(1.dp, Aether.GlassBorderSoft.copy(alpha = .55f), AtelierInsetShape)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 11.dp)
            .semantics { contentDescription = speech },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stages.forEachIndexed { index, stage ->
                RouteNodeCell(label = stage.first, state = stage.second, tone = tone)
                if (index < stages.lastIndex) {
                    val next = stages[index + 1].second
                    RouteLink(
                        state = next,
                        tone = tone,
                        proven = next == MarbleNodeState.PROVEN,

                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Text(
            text = atelierSignetCaption(path),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun RouteNodeCell(label: String, state: MarbleNodeState, tone: Color) {
    val fill = when (state) {
        MarbleNodeState.PROVEN -> tone
        MarbleNodeState.FAILED -> Aether.Danger
        else -> Color.Transparent
    }
    val rim = when (state) {
        MarbleNodeState.PROVEN -> tone
        MarbleNodeState.PENDING -> tone.copy(alpha = .55f)
        MarbleNodeState.FAILED -> Aether.Danger
        MarbleNodeState.UNKNOWN -> Aether.InkFaint.copy(alpha = .45f)
    }
    Column(
        modifier = Modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(fill)
                .border(if (state == MarbleNodeState.PROVEN) 1.dp else 2.dp, rim, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                MarbleNodeState.PROVEN -> Text(
                    text = "✓",
                    color = marbleOnColor(tone),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                MarbleNodeState.FAILED -> Text(
                    text = "×",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                MarbleNodeState.PENDING -> {
                    val motion = MarbleMotion.current
                    val ambient = LocalMarbleAmbientField.current
                    val waiting = ambient && motion.acknowledges(MarbleControlKind.Icon)
                    val alpha = if (waiting) .40f + .38f * motion.breathe(1_600) else .68f
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(tone.copy(alpha = alpha))
                    )
                }
                MarbleNodeState.UNKNOWN -> Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Aether.InkFaint.copy(alpha = .45f))
                )
            }
        }
        Text(
            text = label,
            color = if (state == MarbleNodeState.UNKNOWN) Aether.InkFaint else Aether.InkMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RouteLink(
    state: MarbleNodeState,
    tone: Color,
    proven: Boolean,
    modifier: Modifier = Modifier
) {
    val color = when (state) {
        MarbleNodeState.PROVEN -> tone
        MarbleNodeState.FAILED -> Aether.Danger
        MarbleNodeState.PENDING -> tone.copy(alpha = .45f)
        MarbleNodeState.UNKNOWN -> Aether.InkFaint.copy(alpha = .25f)
    }
    Box(
        modifier = modifier
            .height(if (proven) 2.dp else 1.dp)
            .background(color, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun atelierSignetCaption(path: MarbleRoutePath): String {
    val t = Tr.now
    return when {
        path.isComplete -> t.statusProtected
        path.provenCount == 0 -> t.notMeasured
        else -> "${path.provenCount}/3 • ${trx("stages with evidence")}"
    }
}

@Composable
private fun atelierSignetSpeech(path: MarbleRoutePath): String {
    val t = Tr.now
    fun word(state: MarbleNodeState): String = when (state) {
        MarbleNodeState.PROVEN -> t.statusProtected
        MarbleNodeState.PENDING -> t.measuring
        MarbleNodeState.FAILED -> t.pingFailed
        MarbleNodeState.UNKNOWN -> t.notMeasured
    }
    return "${trx("Device")}: ${word(path.device)}, ${trx("Tunnel")}: ${word(path.tunnel)}, ${
        trx("Exit")
    }: ${word(path.egress)}"
}

@Composable
private fun RouteSelectedServerCard(evidence: HomeEvidence, actions: HomeActions) {
    val t = Tr.now
    val profile = evidence.profile
    HomeCloudCard(modifier = Modifier.fillMaxWidth(), shape = AtelierCardShape) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 6.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProtocolTile(
                    scheme = profile?.scheme.orEmpty(),
                    size = 32.dp,
                    flag = evidence.flag.takeIf { it.isNotBlank() && evidence.mayPaintLocationFlag },
                    flagCode = evidence.flagCode.takeIf { it.isNotBlank() && evidence.mayPaintLocationFlag },

                    locationTrust = evidence.locationTrust,
                    stateTone = if (evidence.routeState == MarbleRouteState.CONNECTED) {
                        marbleRouteTone(evidence.routeState)
                    } else {
                        null
                    }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = evidence.nodeName.ifBlank { t.noServersTitle },
                        color = Aether.Ink,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = atelierSourceLine(evidence, profile),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AtelierTextAction(label = trx("Change server"), onClick = { actions.onLibrary() })
                if (evidence.ip.isNotBlank()) {
                    AtelierTextAction(label = t.copyIp, onClick = { actions.onCopyIp() })
                }
                Box(modifier = Modifier.weight(1f))
                if (profile != null) {
                    AtelierTextAction(label = t.ipDetails, onClick = { actions.onIpDetails() })
                }
            }
        }
    }
}

@Composable
private fun atelierSourceLine(evidence: HomeEvidence, profile: ProxyProfile?): String {
    val t = Tr.now
    if (profile == null) return t.noServersBody
    val source = evidence.sourceName.takeIf { it.isNotBlank() && !it.equals("Manual", true) }
        ?: t.localLabel
    return "${t.source}: $source"
}

@Composable
private fun AtelierTextAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .marbleTapTarget()
            .clip(AtelierInsetShape)
            .kineticClickable(role = Role.Button, boundedShape = AtelierInsetShape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Aether.Cyan,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RouteQualityCard(repo: AppRepository, evidence: HomeEvidence) {
    val latency = homePingLabel(evidence)
    val latencyTone = homePingTone(evidence, marbleRouteTone(evidence.routeState))
    val jitter = repo.liveJitterMs
    HomeCloudCard(modifier = Modifier.fillMaxWidth(), shape = AtelierCardShape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AtelierMetric(
                label = trx("Latency"),
                value = latency,
                unit = "",
                tone = latencyTone,
                missing = evidence.pingMs <= 0,
                modifier = Modifier.weight(1f)
            )
            AtelierMetricDivider()
            AtelierMetric(
                label = trx("Jitter"),
                value = if (jitter > 0) jitter.toString() else "—",
                unit = if (jitter > 0) "ms" else "",
                tone = Aether.Amethyst,
                missing = jitter <= 0,
                modifier = Modifier.weight(1f)
            )
            AtelierMetricDivider()
            AtelierMetric(
                label = trx("Quality"),
                value = evidence.stabilityClass.ifBlank { "—" },
                unit = "",
                tone = marbleRouteTone(evidence.routeState),
                missing = evidence.stabilityClass.isBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AtelierMetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(Aether.GlassBorderSoft)
    )
}

@Composable
private fun AtelierMetric(
    label: String,
    value: String,
    unit: String,
    tone: Color,
    missing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = if (missing) Aether.InkFaint else tone,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = if (missing) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
        Text(
            text = if (missing) Tr.now.notMeasured else "",
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
