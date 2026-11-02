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
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

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
    val colors = MaterialTheme.colorScheme
    val plan = repo.councilPlan
    val report = repo.councilReport
    val evidence = repo.councilEvidence
    val running = repo.councilRunning

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.RADAR,
        tint = colors.tertiary,
        title = trx("Network check"),
        onBack = onBack
    ) {
        item(key = "council-hero") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Run the check"),
                    badge = if (plan != null) plan.metrics.grade else "",
                    badgeColor = plan?.let { gradeColor(it.metrics.score) }
                )
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MarbleRowIcon(
                                glyph = MarbleGlyph.RADAR,
                                tint = if (running) colors.warning else colors.primary,
                                size = 44.dp,
                                glyphSize = 22.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = trx("Twelve checks, one plan"),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = linkLine(evidence, report),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (plan != null) {
                                MarbleBadge(
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
                                        .background(colors.surfaceContainer)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(repo.councilProgress.coerceIn(0f, 1f))
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(colors.primary)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = trx(repo.councilStage.ifBlank { "Measuring" }),
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "${(repo.councilProgress * 100f).roundToInt()}%",
                                        color = colors.primary,
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
                            MarbleButton(
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
                            // MARBLE_ONE_RESET_V261 (the duplicate-control sweep) — one apply verb.
                            //
                            // This button and "Apply these settings" at the bottom of the findings
                            // card were the same call behind the same gate: scroll half a screen and
                            // the page offered to do the same thing again, in the other button
                            // style, under a second label. The one that stays is this one, beside
                            // the scan verb and the progress bar — the place a reader is already
                            // looking when the plan arrives — and the label now says what applying
                            // *does* rather than how many rows it touches.
                            if (plan != null && plan.changes.isNotEmpty() && !running) {
                                MarbleButton(
                                    label = trx("Apply these settings"),
                                    glyph = MarbleGlyph.CHECK,
                                    style = MarbleButtonStyle.TINTED,
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
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item(key = "council-seats") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("The twelve checks"),
                    badge = if (plan == null) "" else faDigits("${plan.findings.size}"),
                    badgeColor = colors.secondary
                )
                MarbleCard {
                    NetworkCouncil.CouncilSeat.entries.forEachIndexed { index, seat ->
                        if (index > 0) MarbleDivider()
                        val severity = repo.councilVerdict(seat)
                        val finding = plan?.findings?.firstOrNull { it.seat == seat }
                        MarbleListItem(
                            title = trx(seat.label),
                            subtitle = finding?.headline?.let { trx(it) } ?: trx(seat.focus),
                            glyph = seatGlyph(seat),
                            iconTint = severityColor(severity),
                            showChevron = false,
                            value = severityLabel(severity)
                        )
                    }
                }
            }
        }

        if (plan != null) {
            item(key = "council-changes") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSectionHeader(
                        title = trx("What it would change"),
                        badge = faDigits("${plan.changes.size}"),
                        badgeColor = colors.success
                    )
                    if (plan.changes.isEmpty()) {
                        MarbleEmptyState(
                            title = trx("Nothing to change"),
                            body = trx(
                                "The scan agreed with every setting already on this device — the council does not rewrite a knob for the pleasure of it."
                            ),
                            glyph = MarbleGlyph.CHECK,
                            tint = colors.success
                        )
                    } else {
                        MarbleCard {
                            plan.changes.forEachIndexed { index, change ->
                                if (index > 0) MarbleDivider()
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
                                            color = colors.onSurface,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${displayValue(change.field, change.previous)} → " +
                                                displayValue(change.field, change.value),
                                            color = colors.primary,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = trx(change.reason),
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MarbleBadge(
                                            text = trx(change.seat.label),
                                            color = severityColor(repo.councilVerdict(change.seat))
                                        )
                                        MarbleBadge(
                                            text = "${change.confidence}%",
                                            color = colors.tertiary
                                        )
                                    }
                                    if (change.dissent.isNotBlank()) {
                                        Text(
                                            text = trx("Objected") + " · " + trx(change.dissent),
                                            color = colors.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        // The trailing row of the findings card is a *footer*, not a second verb:
                        // the apply is at the top of the page beside the scan that produced these
                        // rows, and repeating it here was the duplicate this pass removes. A footer
                        // that says what the button does is what belongs under a list of reasons.
                        val changes = plan?.changes.orEmpty()
                        if (!running && changes.isNotEmpty()) {
                            val applyFooter = trx(
                                "Applying takes the changes above and nothing else — revert any of them in Routing."
                            )
                            MarbleSectionFooter(applyFooter)
                        }
                    }
                }
            }
        }

        if (evidence != null && plan != null) {
            item(key = "council-measurements") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSectionHeader(title = trx("What we measured"))
                    MarbleCard {
                        CouncilMetricRow(
                            label = "Transport",
                            value = "${trx(evidence.radio.transport.label)} · ${evidence.radio.networkType}"
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "Operator",
                            value = evidence.radio.operatorName.ifBlank { trx("Unknown") }
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "Signal",
                            value = if (evidence.radio.signalLevel < 0) {
                                trx("Redacted by Android")
                            } else {
                                "${evidence.radio.signalLevel}/4" +
                                    if (evidence.radio.signalDbm != 0) " · ${evidence.radio.signalDbm} dBm" else ""
                            }
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "Link estimate",
                            value = "${kbps(evidence.radio.linkDownKbps)} ↓ · ${kbps(evidence.radio.linkUpKbps)} ↑"
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "Latency",
                            value = "${plan.metrics.medianRttMs.roundToInt()} ms median · " +
                                "${plan.metrics.p95RttMs.roundToInt()} ms p95 · " +
                                "${plan.metrics.jitterMs.roundToInt()} ms jitter",
                            tone = gradeColor(plan.metrics.score)
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "Loss",
                            value = "${plan.metrics.lossPercent.roundToInt()}% of " +
                                "${evidence.latency.attempts} handshakes"
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "DNS",
                            value = "${plan.metrics.dnsOkPercent.roundToInt()}% answered · " +
                                "${plan.metrics.dnsMedianMs.roundToInt()} ms median"
                        )
                        evidence.dns.forEach { entry ->
                            MarbleDivider()
                            CouncilMetricRow(
                                label = entry.server,
                                value = if (entry.answered) {
                                    "${entry.latencyMs} ms · rcode ${entry.rcode}" +
                                        if (entry.blockPage) " · BLOCK PAGE" else ""
                                } else {
                                    trx("No answer")
                                },
                                tone = when {
                                    entry.blockPage -> colors.error
                                    !entry.answered -> colors.warning
                                    else -> colors.onSurface
                                }
                            )
                        }
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "Ports",
                            value = "${plan.metrics.openPorts}/${plan.metrics.probedPorts} · " +
                                evidence.ports.filter { it.open }.map { it.port }
                                    .sorted()
                                    .joinToString(", ")
                                    .ifBlank { trx("none") }
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "UDP",
                            value = when {
                                evidence.path.quicOk -> trx("QUIC and UDP answer")
                                evidence.path.udpDnsOk -> trx("Plain UDP answers, QUIC does not")
                                else -> trx("No UDP answer")
                            }
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "MTU",
                            value = if (evidence.path.pathMtu > 0) {
                                "${evidence.path.pathMtu} bytes · " +
                                    "${(evidence.path.mtuConfidence * 100).roundToInt()}% confidence"
                            } else {
                                trx("Not measured") + " · " +
                                    trx("link") + " " + faDigits("${evidence.radio.interfaceMtu}")
                            }
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "Address family",
                            value = when {
                                evidence.path.ipv4Ok && evidence.path.ipv6Ok -> "IPv4 ✓ · IPv6 ✓"
                                evidence.path.ipv4Ok -> "IPv4 ✓ · IPv6 ✕"
                                evidence.path.ipv6Ok -> "IPv4 ✕ · IPv6 ✓"
                                else -> "IPv4 ✕ · IPv6 ✕"
                            }
                        )
                        MarbleDivider()
                        CouncilMetricRow(
                            label = "TLS handshake",
                            value = when {
                                evidence.path.injectedReset -> trx("Injected reset")
                                evidence.path.tlsCompleted -> trx("Completed")
                                else -> trx("Did not complete")
                            },
                            tone = when {
                                evidence.path.injectedReset -> colors.error
                                evidence.path.tlsCompleted -> colors.success
                                else -> colors.warning
                            }
                        )
                        MarbleDivider()
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
                    MarbleSectionHeader(title = trx("Why each one voted"))
                    MarbleCard {
                        plan.findings.forEachIndexed { index, finding ->
                            if (index > 0) MarbleDivider()
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
                                        color = colors.onSurface,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    MarbleBadge(
                                        text = severityLabel(finding.severity),
                                        color = severityColor(finding.severity)
                                    )
                                }
                                Text(
                                    text = trx(finding.headline),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                if (finding.detail.isNotBlank()) {
                                    Text(
                                        text = trx(finding.detail),
                                        color = colors.onSurfaceVariant,
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
                    MarbleSectionHeader(title = trx("Last saved result"))
                    MarbleCard {
                        CouncilMetricRow(
                            label = report.operatorName.ifBlank {
                                report.networkType.ifBlank { report.transport.label }
                            },
                            value = faDigits("${report.score}") + " " + report.grade + " · " + faDigits("${report.changes.size}") + " " + trx("changes")
                        )
                    }
                }
            }
        }

        item(key = "council-notes") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Apply what the check suggests, without asking"),
                        subtitle = trx(
                            "Off: the council still scans and explains, but every change waits for your tap."
                        ),
                        checked = repo.councilAutoApply,
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateCouncilAutoApply(it) }
                    )
                    val saved = repo.councilReport
                    if (saved != null) {
                        MarbleDivider()
                        // MARBLE_ROUTING_REBUILD_V254 — this was a switch whose only state was
                        // "off": flicking it cleared the report and it snapped straight back. A row
                        // with a button says what it does and stays honest.
                        MarbleListItem(
                            title = trx("Forget the last result"),
                            subtitle = listOf(saved.summary, repo.councilScanAgeLabel())
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            glyph = MarbleGlyph.TRASH,
                            iconTint = colors.onSurfaceVariant,
                            showChevron = false,
                            value = trx("Forget"),
                            onClick = { repo.clearNetworkCouncilReport() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CouncilMetricRow(
    label: String,
    value: String,
    tone: Color = MaterialTheme.colorScheme.onSurface
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = trx(label),
            color = colors.onSurfaceVariant,
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
        return trx("Last result") + " · " + where + " · " + "${report.score} ${report.grade}"
    }
    return trx("Wi-Fi, mobile data and the operator behind them")
}

@Composable
private fun severityColor(severity: NetworkCouncil.CouncilSeverity): Color = when (severity) {
    NetworkCouncil.CouncilSeverity.GOOD -> MaterialTheme.colorScheme.success
    NetworkCouncil.CouncilSeverity.NOTICE -> MaterialTheme.colorScheme.primary
    NetworkCouncil.CouncilSeverity.WARNING -> MaterialTheme.colorScheme.warning
    NetworkCouncil.CouncilSeverity.CRITICAL -> MaterialTheme.colorScheme.error
}

@Composable
private fun gradeColor(score: Int): Color = when {
    score >= 85 -> MaterialTheme.colorScheme.success
    score >= 70 -> MaterialTheme.colorScheme.tertiary
    score >= 55 -> MaterialTheme.colorScheme.warning
    else -> MaterialTheme.colorScheme.error
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
