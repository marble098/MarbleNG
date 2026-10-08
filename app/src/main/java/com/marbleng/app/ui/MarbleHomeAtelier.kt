package com.marbleng.app.ui

// MARBLE_ROUTE_ATELIER_V207 — the fifth Home presentation: "Route".
//
// The V207 review's complaint about Home was not that it was ugly; it was that it had no single
// grammar. Four presentations, two card families, five silhouettes for one command, color borrowed for
// brand, state and category at the same time, and a control that answered three different questions
// ("connect", "change server", "cancel"). This file is that grammar written down as a page: one card
// radius, one depth rule, one border rule, one ink ramp, and one meaning per channel — cyan is the
// brand, the state hues are state, and a protocol hue lives only inside its small identity tile.
//
// It is an *additional* presentation, not a replacement. The four presentations that existed before
// are untouched, which is both what the review asked for and how a redesign earns its place: the old
// page stays one switch away while the new one proves itself. Nothing here deletes a server field, an
// editor or a control — the page consumes the same [HomeEvidence] as every other presentation and
// reuses the shared feature widgets, so a feature cannot quietly go missing in the transfer.
//
// The one thing this page adds that no other presentation has is the [RoutePathSignet]: three nodes
// between this device and its exit, each painted only for the state there is evidence of. It exists
// because a VPN client's central promise — "your traffic leaves through somewhere else" — used to be
// represented by a glowing circle, and a glow is a mood, not a fact.

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

/** The one card radius this presentation uses — 20 dp, every card, every density. */
private val AtelierCardShape = RoundedCornerShape(20.dp)

/** The one inset radius (the signet sits inside a card, so it steps down one notch). */
private val AtelierInsetShape = RoundedCornerShape(16.dp)

/**
 * The route presentation of Home: header, national-event banner, one decision card, the server that
 * decision acts on, the measured quality of the path, session totals, shortcuts — in that order,
 * which is the order of the questions a user arrives with: am I protected, through what, how well,
 * and for how long.
 */
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
        NationalEventBanner(repo = repo)
        RouteConnectCard(evidence = evidence, actions = actions)
        RouteSelectedServerCard(evidence = evidence, actions = actions)
        // MARBLE_SERVER_TILE_LAYOUT_V208 — the page that names the route also lets you change it.
        // The route presentation had no server list at all, so choosing a different server meant
        // leaving Home; the shared library box (rows or tiles, whichever the user picked in
        // Settings) is composed here exactly as the other presentations compose it, so the
        // presentation gains a feature without growing a private one.
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
            // MARBLE_HOME_PING_CONTROLS_V212 — the deck's pill is a control; its verb is the
            // user's, resolved once in the evidence block.
            pingAction = evidence.pingChipAction
        )
    }
}

// -------------------------------------------------------------------------------------------------
// The decision card: one sentence of state, the path, the one verb, and any failure beside its way out
// -------------------------------------------------------------------------------------------------

/**
 * The card that answers the first question. One sentence of status, the [RoutePathSignet] under it,
 * the verb the same state resolves to, and — when something is wrong — the failure with its corrective
 * action inside the same box, because a problem and its way out must not live two screens apart.
 */
@Composable
private fun RouteConnectCard(evidence: HomeEvidence, actions: HomeActions) {
    val state = evidence.routeState
    // The state hue is the meaning of this card, so it is the one thing allowed to move when the
    // state changes — and it moves for the contracted 200 ms, no longer. With reduced motion the
    // colour is simply different on the next frame: a state change must be legible, not followed.
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
                    // The state hue is the whole point of this line. The size ramp tops out at 24 sp:
                    // the sentence carries the page, not the type.
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
            // The one action. Full width, 56 dp tall, and labelled by the same state that decides
            // what it does — the two are produced from one value, so they cannot drift apart.
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
            val problem = atelierProblem(evidence)
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
                    // The corrective action is the measurement again: this line only exists when
                    // the last answer was a failure, so "retry" means "measure again" — and what
                    // failed is the ROUTE this page is showing, not the subscription behind it.
                    // It is therefore the same verb as the header's pulse and the split control's
                    // second disc (MARBLE_HOME_ROUTE_PING_V210), not a second measurement that
                    // answers a different question.
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

/**
 * The one explanatory line under the status, and every clause in it is something the app can prove:
 * the exit address once one has been read, then the path's shape (protocol, transport), then where
 * the endpoint is *if a measurement says so* — never a country guessed from the node's own name.
 */
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

/** The failure line, if there is one — and only what is actually known about it. */
@Composable
private fun atelierProblem(evidence: HomeEvidence): String? {
    val t = Tr.now
    return when {
        evidence.blocked -> evidence.pingFailure.ifBlank { t.connectionStopped }
        evidence.pingState == ConnectionPingState.FAILED && evidence.pingFailure.isNotBlank() ->
            pingFailureLabel(evidence.pingFailure)
        evidence.ipError -> trx("The exit address could not be read. The tunnel is up; the reading service did not answer.")
        else -> null
    }
}

// -------------------------------------------------------------------------------------------------
// The route signet: three stages, each painted only for the state there is evidence of
// -------------------------------------------------------------------------------------------------

/**
 * Device → tunnel → exit.
 *
 * The brief's "a diagram with three states" is what this is for: the page's central claim is that
 * traffic leaves through somewhere else, and most of that claim used to be carried by a glow. Each
 * node now takes its look from a state the app can back up, and an unproven stage is drawn as
 * unproven — hollow and quiet, never "pending with confidence".
 *
 * A connector belongs to the stage it leads into, so the eye reads the path as a sequence and not as
 * three separate badges. Nothing here animates on its own account: a pulse is reserved for a stage
 * that is genuinely in progress, and even that one holds still when the ambient field is off, so the
 * diagram never pretends to be working.
 */
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
    // The spoken form is resolved here, not inside the semantics block: a modifier's semantics lambda
    // is not a composable scope, so the words the page reads aloud have to be produced before it.
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
                        // How much of the row a connector may claim is the Row's business, so it is
                        // measured at the call site: `weight` is a `RowScope` extension and cannot be
                        // named from inside [RouteLink]'s own body.
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

/** One stage of the path: a proven disc, a waiting ring, a failed stop, or a hollow unknown. */
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
                    // The one moving thing on this mark, and it means exactly one thing: a stage is
                    // waiting on an answer it does not have yet. A proven node never breathes, an
                    // unproven one never pretends to, and the ambient switch settles the question for
                    // good — with it off the dot holds its alpha and the diagram stops costing frames.
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

/** The line between two stages: solid when the stage it leads into is proven, hairline when it is not. */
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

/**
 * The signet read aloud. TalkBack gets each stage as a word, because a check mark, a hollow ring and a
 * dash are precisely the distinctions a screen reader cannot see.
 */
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

// -------------------------------------------------------------------------------------------------
// The server the verb acts on, and how it got here
// -------------------------------------------------------------------------------------------------

/**
 * The route the connect card would act on: its identity tile, its name, where it came from, and the one
 * control that changes it.
 *
 * This is also where the review's separation of duties lives. Choosing a route and running it are two
 * actions with two homes: the row below opens the Servers page and never connects anything, and the
 * verb above never picks a server by itself.
 */
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
                    // The tile inherits the card's honesty: a lone reading and a name-emoji guess are
                    // drawn as what they are, and an unconfirmed location gets the dashed rim.
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

/** Where this route came from — the subscription it was pulled with, or the fact that it is local. */
@Composable
private fun atelierSourceLine(evidence: HomeEvidence, profile: ProxyProfile?): String {
    val t = Tr.now
    if (profile == null) return t.noServersBody
    val source = evidence.sourceName.takeIf { it.isNotBlank() && !it.equals("Manual", true) }
        ?: t.localLabel
    return "${t.source}: $source"
}

/** A text-shaped action that still clears the 48 dp floor. */
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

// -------------------------------------------------------------------------------------------------
// Quality: measurements, each either backed by a sample or saying that it is not
// -------------------------------------------------------------------------------------------------

/**
 * The path's measured quality: three numbers a user could act on, never a decorative gauge. A cell
 * with no sample shows a dash and the words "not measured", because a placeholder that looks like a
 * zero is worse than an honest absence — and the dash is the same character every other presentation
 * already uses for the same case.
 */
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
