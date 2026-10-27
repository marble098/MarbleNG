package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.CouncilReport
import com.marbleng.app.core.NetworkCouncil
import kotlin.math.roundToInt

/**
 * MARBLE_NETWORK_COUNCIL_V236 — the page the twelve specialists report to.
 *
 * It is built as a conversation, in the order the council works: *who is on this network* (the
 * link), *what each specialist measured* (the seat table), *what that costs in settings* (the
 * changes, with the value each one replaces), and *what the raw measurements were* (so a claim on
 * this page can always be checked against a number). Nothing here is recomposed into a claim the
 * council did not make: every row is either evidence or a decision that cites its seat.
 */
@Composable
fun SettingsNetworkCouncilPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val plan = repo.councilPlan
    val report = repo.councilReport
    val evidence = repo.councilEvidence
    val running = repo.councilRunning

    IosSubPageScaffold(
        title = trx("Network Council Scanner"),
        onBack = onBack
    ) {
        item(key = "council-hero") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(
                    title = trx("Commission the council"),
                    badge = if (plan != null) plan.metrics.grade else "",
                    badgeColor = plan?.let { gradeColor(it.metrics.score) }
                )
                IosCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IosSquircleIcon(
                                glyph = MarbleGlyph.RADAR,
                                backgroundColor = if (running) colors.orange else colors.blue,
                                size = 44.dp,
                                glyphSize = 22.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = trx("Twelve specialists · one plan"),
                                    color = colors.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = linkLine(evidence, report),
                                    color = colors.subtitle,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (plan != null) {
                                IosBadge(
                                    text = "${plan.metrics.score} ${plan.metrics.grade}",
                                    color = gradeColor(plan.metrics.score)
                                )
                            }
                        }

                        if (running) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(colors.cardSecondary)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(repo.councilProgress.coerceIn(0f, 1f))
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(colors.accent)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = trx(repo.councilStage.ifBlank { "Measuring" }),
                                        color = colors.subtitle,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "${(repo.councilProgress * 100f).roundToInt()}%",
                                        color = colors.accent,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IosButton(
                                label = if (running) {
                                    trx("Scanning")
                                } else if (plan == null && report == null) {
                                    trx("Start scan")
                                } else {
                                    trx("Scan again")
                                },
                                glyph = MarbleGlyph.RADAR,
                                enabled = !running,
                                onClick = { repo.startNetworkCouncilScan() },
                                modifier = Modifier.weight(1f)
                            )
                            if (plan != null && plan.changes.isNotEmpty() && !running) {
                                IosButton(
                                    label = "${trx("Apply")} ${plan.changes.size}",
                                    glyph = MarbleGlyph.CHECK,
                                    style = IosButtonStyle.TINTED,
                                    onClick = { repo.applyNetworkCouncilPlan() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (plan == null && report == null && !running) {
                            Text(
                                text = trx(
                                    "One scan reads this link, this operator and this path — ping, TCP, UDP, DNS, ports and MTU — then writes the settings it can justify on both cores."
                                ),
                                color = colors.subtitle,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item(key = "council-seats") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(
                    title = trx("The twelve seats"),
                    badge = if (plan == null) "" else "${plan.findings.size}",
                    badgeColor = colors.indigo
                )
                IosCard {
                    NetworkCouncil.CouncilSeat.entries.forEachIndexed { index, seat ->
                        if (index > 0) IosDivider()
                        val severity = repo.councilVerdict(seat)
                        val finding = plan?.findings?.firstOrNull { it.seat == seat }
                        IosListItem(
                            title = trx(seat.label),
                            subtitle = finding?.headline?.let { trx(it) } ?: trx(seat.focus),
                            glyph = seatGlyph(seat),
                            squircleColor = severityColor(severity),
                            showChevron = false,
                            value = severityLabel(severity)
                        )
                    }
                }
                if (plan != null) {
                    IosSectionFooter(
                        text = trx(
                            "A seat that measured nothing speaks at lower confidence, and the operator table can only advise a completed handshake probe — never overrule it."
                        )
                    )
                }
            }
        }

        if (plan != null) {
            item(key = "council-changes") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosSectionHeader(
                        title = trx("Settings the council would write"),
                        badge = "${plan.changes.size}",
                        badgeColor = colors.green
                    )
                    if (plan.changes.isEmpty()) {
                        IosEmptyState(
                            title = trx("Nothing to change"),
                            body = trx(
                                "The scan agreed with every setting already on this device — the council does not rewrite a knob for the pleasure of it."
                            ),
                            glyph = MarbleGlyph.CHECK,
                            tint = colors.green
                        )
                    } else {
                        IosCard {
                            plan.changes.forEachIndexed { index, change ->
                                if (index > 0) IosDivider()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = trx(change.field.label),
                                            color = colors.title,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${displayValue(change.field, change.previous)} → " +
                                                displayValue(change.field, change.value),
                                            color = colors.accent,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = trx(change.reason),
                                        color = colors.subtitle,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IosBadge(
                                            text = trx(change.seat.label),
                                            color = severityColor(repo.councilVerdict(change.seat))
                                        )
                                        IosBadge(
                                            text = "${change.confidence}%",
                                            color = colors.teal
                                        )
                                    }
                                    if (change.dissent.isNotBlank()) {
                                        Text(
                                            text = trx("Dissent") + " · " + trx(change.dissent),
                                            color = colors.tertiary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        if (!running) {
                            IosButton(
                                label = trx("Apply the council's settings"),
                                glyph = MarbleGlyph.SPARK,
                                onClick = { repo.applyNetworkCouncilPlan() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        if (evidence != null && plan != null) {
            item(key = "council-measurements") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosSectionHeader(title = trx("What was measured"))
                    IosCard {
                        CouncilMetricRow(
                            label = "Transport",
                            value = "${trx(evidence.radio.transport.label)} · ${evidence.radio.networkType}"
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "Operator",
                            value = evidence.radio.operatorName.ifBlank { trx("Unknown") }
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "Signal",
                            value = if (evidence.radio.signalLevel < 0) {
                                trx("Redacted by Android")
                            } else {
                                "${evidence.radio.signalLevel}/4" +
                                    if (evidence.radio.signalDbm != 0) " · ${evidence.radio.signalDbm} dBm" else ""
                            }
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "Link estimate",
                            value = "${kbps(evidence.radio.linkDownKbps)} ↓ · ${kbps(evidence.radio.linkUpKbps)} ↑"
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "Latency",
                            value = "${plan.metrics.medianRttMs.roundToInt()} ms median · " +
                                "${plan.metrics.p95RttMs.roundToInt()} ms p95 · " +
                                "${plan.metrics.jitterMs.roundToInt()} ms jitter",
                            tone = gradeColor(plan.metrics.score)
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "Loss",
                            value = "${plan.metrics.lossPercent.roundToInt()}% of " +
                                "${evidence.latency.attempts} handshakes"
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "DNS",
                            value = "${plan.metrics.dnsOkPercent.roundToInt()}% answered · " +
                                "${plan.metrics.dnsMedianMs.roundToInt()} ms median"
                        )
                        evidence.dns.forEach { entry ->
                            IosDivider()
                            CouncilMetricRow(
                                label = entry.server,
                                value = if (entry.answered) {
                                    "${entry.latencyMs} ms · rcode ${entry.rcode}" +
                                        if (entry.blockPage) " · BLOCK PAGE" else ""
                                } else {
                                    trx("No answer")
                                },
                                tone = when {
                                    entry.blockPage -> colors.red
                                    !entry.answered -> colors.orange
                                    else -> colors.title
                                }
                            )
                        }
                        IosDivider()
                        CouncilMetricRow(
                            label = "Ports",
                            value = "${plan.metrics.openPorts}/${plan.metrics.probedPorts} · " +
                                evidence.ports.filter { it.open }.map { it.port }
                                    .sorted()
                                    .joinToString(", ")
                                    .ifBlank { trx("none") }
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "UDP",
                            value = when {
                                evidence.path.quicOk -> trx("QUIC and UDP answer")
                                evidence.path.udpDnsOk -> trx("Plain UDP answers, QUIC does not")
                                else -> trx("No UDP answer")
                            }
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "MTU",
                            value = if (evidence.path.pathMtu > 0) {
                                "${evidence.path.pathMtu} bytes · " +
                                    "${(evidence.path.mtuConfidence * 100).roundToInt()}% confidence"
                            } else {
                                trx("Not measured") + " · " +
                                    trx("link") + " ${evidence.radio.interfaceMtu}"
                            }
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "Address family",
                            value = when {
                                evidence.path.ipv4Ok && evidence.path.ipv6Ok -> "IPv4 ✓ · IPv6 ✓"
                                evidence.path.ipv4Ok -> "IPv4 ✓ · IPv6 ✕"
                                evidence.path.ipv6Ok -> "IPv4 ✕ · IPv6 ✓"
                                else -> "IPv4 ✕ · IPv6 ✕"
                            }
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "TLS handshake",
                            value = when {
                                evidence.path.injectedReset -> trx("Injected reset")
                                evidence.path.tlsCompleted -> trx("Completed")
                                else -> trx("Did not complete")
                            },
                            tone = when {
                                evidence.path.injectedReset -> colors.red
                                evidence.path.tlsCompleted -> colors.green
                                else -> colors.orange
                            }
                        )
                        IosDivider()
                        CouncilMetricRow(
                            label = "Device",
                            value = "${evidence.device.cores} cores · " +
                                (if (evidence.device.memoryMb > 0) "${evidence.device.memoryMb} MB · " else "") +
                                "thermal ${evidence.device.thermalStatus}"
                        )
                    }
                }
            }
        }

        if (plan != null) {
            item(key = "council-rationale") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosSectionHeader(title = trx("Why each seat ruled"))
                    IosCard {
                        plan.findings.forEachIndexed { index, finding ->
                            if (index > 0) IosDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = trx(finding.seat.label),
                                        color = colors.title,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    IosBadge(
                                        text = severityLabel(finding.severity),
                                        color = severityColor(finding.severity)
                                    )
                                }
                                Text(
                                    text = trx(finding.headline),
                                    color = colors.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                if (finding.detail.isNotBlank()) {
                                    Text(
                                        text = trx(finding.detail),
                                        color = colors.tertiary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (report != null && !report.applied) {
            item(key = "council-last-report") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosSectionHeader(title = trx("Last saved verdict"))
                    IosCard {
                        CouncilMetricRow(
                            label = report.operatorName.ifBlank {
                                report.networkType.ifBlank { report.transport.label }
                            },
                            value = "${report.score} ${report.grade} · ${report.changes.size} " + trx("settings")
                        )
                    }
                }
            }
        }

        item(key = "council-notes") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosCard {
                    IosSwitchRow(
                        title = trx("Apply the verdict automatically"),
                        subtitle = trx(
                            "Off: the council still scans and explains, but every change waits for your tap."
                        ),
                        checked = repo.councilAutoApply,
                        glyph = MarbleGlyph.CHECK,
                        squircleColor = colors.teal,
                        onCheckedChange = { repo.setCouncilAutoApply(it) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Forget the last verdict"),
                        subtitle = repo.councilReport?.let { saved ->
                            listOf(saved.summary, repo.councilScanAgeLabel())
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                        } ?: trx("Nothing saved yet"),
                        checked = false,
                        glyph = MarbleGlyph.TRASH,
                        squircleColor = colors.gray,
                        onCheckedChange = { if (it) repo.clearNetworkCouncilReport() }
                    )
                }
                IosSectionFooter(
                    text = trx(
                        "The scan downloads nothing: throughput is the link's own estimate, and every probe is individually timed out. Routing, split tunnelling, identity and certificate verification are never touched by the council."
                    )
                )
            }
        }
    }
}

@Composable
private fun CouncilMetricRow(
    label: String,
    value: String,
    tone: Color = IosTheme.colors.title
) {
    val colors = IosTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = trx(label),
            color = colors.subtitle,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = tone,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 2
        )
    }
}

@Composable
private fun linkLine(
    evidence: NetworkCouncil.NetworkEvidence?,
    report: CouncilReport?
): String {
    if (evidence != null) {
        val transport = trx(evidence.radio.transport.label)
        val operator = evidence.radio.operatorName.ifBlank { evidence.radio.networkType }
        return when {
            operator.isBlank() -> transport
            evidence.radio.metered -> "$transport · $operator · ${trx("metered")}"
            else -> "$transport · $operator"
        }
    }
    if (report != null) {
        val where = report.operatorName.ifBlank {
            report.networkType.ifBlank { trx(report.transport.label) }
        }
        return trx("Last verdict") + " · " + where + " · " + "${report.score} ${report.grade}"
    }
    return trx("Wi-Fi, mobile data and the operator behind them")
}

@Composable
private fun severityColor(severity: NetworkCouncil.CouncilSeverity): Color = when (severity) {
    NetworkCouncil.CouncilSeverity.GOOD -> IosTheme.colors.green
    NetworkCouncil.CouncilSeverity.NOTICE -> IosTheme.colors.blue
    NetworkCouncil.CouncilSeverity.WARNING -> IosTheme.colors.orange
    NetworkCouncil.CouncilSeverity.CRITICAL -> IosTheme.colors.red
}

@Composable
private fun gradeColor(score: Int): Color = when {
    score >= 85 -> IosTheme.colors.green
    score >= 70 -> IosTheme.colors.teal
    score >= 55 -> IosTheme.colors.orange
    else -> IosTheme.colors.red
}

@Composable
private fun severityLabel(severity: NetworkCouncil.CouncilSeverity): String = when (severity) {
    NetworkCouncil.CouncilSeverity.GOOD -> trx("clear")
    NetworkCouncil.CouncilSeverity.NOTICE -> trx("notice")
    NetworkCouncil.CouncilSeverity.WARNING -> trx("warning")
    NetworkCouncil.CouncilSeverity.CRITICAL -> trx("critical")
}

private fun seatGlyph(seat: NetworkCouncil.CouncilSeat): MarbleGlyph = when (seat) {
    NetworkCouncil.CouncilSeat.RADIO -> MarbleGlyph.RADAR
    NetworkCouncil.CouncilSeat.OPERATOR -> MarbleGlyph.SERVER
    NetworkCouncil.CouncilSeat.LATENCY -> MarbleGlyph.GAUGE
    NetworkCouncil.CouncilSeat.JITTER -> MarbleGlyph.SPARK
    NetworkCouncil.CouncilSeat.LOSS -> MarbleGlyph.DOWNLOAD
    NetworkCouncil.CouncilSeat.DNS -> MarbleGlyph.GLOBE
    NetworkCouncil.CouncilSeat.PORTS -> MarbleGlyph.LINK
    NetworkCouncil.CouncilSeat.UDP -> MarbleGlyph.ROUTE
    NetworkCouncil.CouncilSeat.MTU -> MarbleGlyph.LAYERS
    NetworkCouncil.CouncilSeat.FAMILY -> MarbleGlyph.GLOBE
    NetworkCouncil.CouncilSeat.DPI -> MarbleGlyph.SHIELD
    NetworkCouncil.CouncilSeat.CORE -> MarbleGlyph.TUNERS
}

private fun displayValue(field: NetworkCouncil.TuningField, raw: String): String = when (field.kind) {
    NetworkCouncil.FieldKind.BOOL -> when (raw.lowercase()) {
        "true" -> "on"
        "false" -> "off"
        else -> raw
    }
    else -> raw.ifBlank { "—" }
}

private fun kbps(value: Int): String = when {
    value <= 0 -> "—"
    value >= 1_000 -> "${(value / 1_000.0).roundToInt()} Mbps"
    else -> "$value kbps"
}
