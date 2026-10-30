package com.marbleng.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.AutoServerSelector
import com.marbleng.app.core.availableOn
import com.marbleng.app.core.coreEngine
import com.marbleng.app.core.unavailableReason
import com.marbleng.app.model.AutoServerScope
import com.marbleng.app.model.AutoServerStrategy
import com.marbleng.app.model.DelayTest
import com.marbleng.app.model.HomePingAction
import com.marbleng.app.model.PingSpeed
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.autoServerScopeEnum
import com.marbleng.app.model.autoServerStrategyEnum
import com.marbleng.app.model.parseHomePingAction
import kotlin.math.roundToInt
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

@Composable
fun SettingsPingTestingPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val engine = s.coreEngine()
    val homeAction = parseHomePingAction(s.homePingGaugeAction)

    MarbleSubPageScaffold(
        title = trx("How ping is measured"),
        onBack = onBack
    ) {
        item(key = "probe-methods") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Measurement Method"))
                MarbleCard {
                    val details = mapOf(
                        ProbeMethod.REAL_DELAY to (trx("Real HTTPS Delay") to trx("Opens an HTTPS request through the tunnel and measures browser-like latency")),
                        ProbeMethod.TCP_PING to (trx("TCP Handshake Ping") to trx("Fastest endpoint liveness check without starting a tunnel")),
                        ProbeMethod.URL_TEST to (trx("Core Native URL Test") to trx("Uses sing-box extended's built-in delay controller"))
                    )
                    ProbeMethod.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) MarbleDivider()
                        val available = candidate.availableOn(engine)
                        val pair = details.getValue(candidate)
                        MarbleChoiceRow(
                            title = pair.first,
                            subtitle = if (available) pair.second else trx(candidate.unavailableReason(engine)),
                            selected = s.probeMethod == candidate,
                            enabled = available,
                            glyph = MarbleGlyph.GAUGE,
                            iconTint = when (candidate) {
                                ProbeMethod.REAL_DELAY -> colors.success
                                ProbeMethod.TCP_PING -> colors.tertiary
                                ProbeMethod.URL_TEST -> colors.secondary
                            },
                            onClick = { repo.updateSettings(s.copy(probeMethod = candidate)) }
                        )
                    }
                }
            }
        }

        item(key = "probe-url-and-budgets") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Test Endpoint & Budgets"))
                MarbleChipStrip {
                    listOf(
                        "Google 204" to DelayTest.URL,
                        "Cloudflare 204" to DelayTest.URL_SECONDARY,
                        "Firefox" to DelayTest.URL_TERTIARY
                    ).forEach { (label, url) ->
                        MarblePillChip(
                            label = label,
                            selected = s.delayTestUrl.trim() == url,
                            onClick = { repo.updateSettings(s.copy(delayTestUrl = url)) }
                        )
                    }
                }
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    MarbleTextField(
                        label = trx("Delay Test URL"),
                        value = s.delayTestUrl,
                        onValueChange = { repo.updateSettings(s.copy(delayTestUrl = it.trim())) }
                    )
                }
                MarbleCard {
                    MarbleNumberStepperRow(
                        title = trx("Ping Timeout"),
                        value = s.pingTimeoutSec,
                        range = 2..20,
                        suffix = "s",
                        glyph = MarbleGlyph.GAUGE,
                        iconTint = colors.primary,
                        onValueChange = { repo.updateSettings(s.copy(pingTimeoutSec = it)) }
                    )
                    MarbleDivider()
                    MarbleNumberStepperRow(
                        title = trx("Samples per Server"),
                        value = s.pingSamples,
                        range = 1..6,
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = colors.secondary,
                        onValueChange = { repo.updateSettings(s.copy(pingSamples = it)) }
                    )
                    MarbleDivider()
                    MarbleNumberStepperRow(
                        title = trx("Parallel Sweep Workers"),
                        value = s.pingConcurrency,
                        range = 2..64,
                        step = 2,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.warning,
                        onValueChange = { repo.updateSettings(s.copy(pingConcurrency = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Custom Sweep Speed"),
                        subtitle = if (s.pingSpeedCustom) {
                            "${PingSpeed.percent(s.pingSpeedPercent)}%"
                        } else {
                            trx("Default fast pace")
                        },
                        checked = s.pingSpeedCustom,
                        glyph = MarbleGlyph.TUNERS,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(pingSpeedCustom = it)) }
                    )
                }

                if (s.pingSpeedCustom) {
                    MarbleCard(contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = trx("Sweep Pace"),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${PingSpeed.percent(s.pingSpeedPercent)}%",
                                    color = colors.primary,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Slider(
                                value = PingSpeed.percent(s.pingSpeedPercent).toFloat(),
                                onValueChange = { v ->
                                    repo.updateSettings(
                                        s.copy(pingSpeedPercent = PingSpeed.stepped(v.toDouble())),
                                        coalesceWrite = true
                                    )
                                },
                                valueRange = PingSpeed.MIN_PERCENT.toFloat()..PingSpeed.MAX_PERCENT.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.primary,
                                    activeTrackColor = colors.primary,
                                    inactiveTrackColor = colors.outlineVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        item(key = "home-ping-action") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Home ping button"))
                MarbleCard {
                    listOf(
                        HomePingAction.ROUTE to trx("Test Active / Selected Server"),
                        HomePingAction.GROUP to trx("Sweep Current Subscription Group"),
                        HomePingAction.LIBRARY to trx("Sweep Entire Server Library"),
                        HomePingAction.TESTS to trx("Open Ping Settings")
                    ).forEachIndexed { idx, (action, label) ->
                        if (idx > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = label,
                            selected = homeAction == action,
                            glyph = MarbleGlyph.HOME,
                            iconTint = colors.success,
                            onClick = {
                                repo.updateSettings(
                                    s.copy(
                                        homePingGaugeAction = action.id,
                                        homePingHeaderAction = action.id
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAutoSelectorPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val strategy = s.autoServerStrategyEnum
    val scope = s.autoServerScopeEnum
    val preview = repo.previewAutoServerChoice()

    MarbleSubPageScaffold(
        title = trx("Pick a server for me"),
        onBack = onBack
    ) {
        item(key = "auto-toggle-strategy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("How it picks"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Enable Automatic Server Selector"),
                        subtitle = trx(AutoServerSelector.describe(strategy)),
                        checked = s.autoServerSelectorEnabled,
                        glyph = MarbleGlyph.RADAR,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(autoServerSelectorEnabled = it)) }
                    )
                    AutoServerStrategy.entries.forEach { candidate ->
                        MarbleDivider()
                        MarbleChoiceRow(
                            title = trx(AutoServerSelector.shortLabel(candidate)),
                            subtitle = trx(AutoServerSelector.describe(candidate)),
                            selected = strategy == candidate,
                            glyph = MarbleGlyph.SPARK,
                            iconTint = colors.secondary,
                            onClick = { repo.updateSettings(s.copy(autoServerStrategy = candidate.id)) }
                        )
                    }
                }
            }
        }

        if (strategy == AutoServerStrategy.CUSTOM) {
            item(key = "auto-custom-weights") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSectionHeader(title = trx("Custom Signal Weights"))
                    MarbleCard {
                        MarbleNumberStepperRow(
                            title = trx("Latency Weight"),
                            value = s.autoServerPingWeight,
                            range = 0..100,
                            step = 5,
                            glyph = MarbleGlyph.GAUGE,
                            iconTint = colors.primary,
                            onValueChange = { repo.updateSettings(s.copy(autoServerPingWeight = it)) }
                        )
                        MarbleDivider()
                        MarbleNumberStepperRow(
                            title = trx("Throughput / Load Weight"),
                            value = s.autoServerLoadWeight,
                            range = 0..100,
                            step = 5,
                            glyph = MarbleGlyph.DOWNLOAD,
                            iconTint = colors.success,
                            onValueChange = { repo.updateSettings(s.copy(autoServerLoadWeight = it)) }
                        )
                        MarbleDivider()
                        MarbleNumberStepperRow(
                            title = trx("Stability Weight"),
                            value = s.autoServerStabilityWeight,
                            range = 0..100,
                            step = 5,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.secondary,
                            onValueChange = { repo.updateSettings(s.copy(autoServerStabilityWeight = it)) }
                        )
                        MarbleDivider()
                        MarbleNumberStepperRow(
                            title = trx("Freshness Weight"),
                            value = s.autoServerFreshnessWeight,
                            range = 0..100,
                            step = 5,
                            glyph = MarbleGlyph.REFRESH,
                            iconTint = colors.warning,
                            onValueChange = { repo.updateSettings(s.copy(autoServerFreshnessWeight = it)) }
                        )
                    }
                }
            }
        }

        item(key = "auto-triggers") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Pool & Triggers"))
                MarbleSegmentedControl(
                    options = listOf(
                        AutoServerScope.SOURCE to trx("Current Source"),
                        AutoServerScope.ALL to trx("Entire Library")
                    ),
                    selected = scope,
                    onSelect = { repo.updateSettings(s.copy(autoServerScope = it.id)) }
                )
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Select After Ping Sweep"),
                        checked = s.autoServerOnScan,
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.success,
                        onCheckedChange = { repo.updateSettings(s.copy(autoServerOnScan = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Choose When Pressing Connect"),
                        checked = s.autoServerOnConnect,
                        glyph = MarbleGlyph.POWER,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(autoServerOnConnect = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Fail Over When Route Drops"),
                        checked = s.autoServerOnFailure,
                        glyph = MarbleGlyph.SHIELD,
                        iconTint = colors.warning,
                        onCheckedChange = { repo.updateSettings(s.copy(autoServerOnFailure = it)) }
                    )
                    MarbleDivider()
                    MarbleNumberStepperRow(
                        title = trx("Switch Hysteresis Margin"),
                        value = s.autoServerSwitchMarginPercent,
                        range = 5..50,
                        step = 5,
                        suffix = "%",
                        glyph = MarbleGlyph.TUNERS,
                        iconTint = colors.secondary,
                        onValueChange = { repo.updateSettings(s.copy(autoServerSwitchMarginPercent = it)) }
                    )
                }
            }
        }

        item(key = "auto-preview") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Current Recommendation"))
                MarbleCard {
                    MarbleListItem(
                        title = preview.profile?.let { cleanServerName(it.name) } ?: trx("Measure servers first"),
                        subtitle = trx(preview.reason),
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.primary,
                        showChevron = false,
                        trailing = {
                            preview.ranking.firstOrNull()?.let { best ->
                                MarbleBadge(
                                    text = "${(best.score * 100).roundToInt()}%",
                                    color = colors.primary
                                )
                            }
                        }
                    )
                }
                MarbleButton(
                    label = if (preview.profile == null) {
                        trx("Measure Candidate Pool")
                    } else {
                        trx("Select Recommended Server")
                    },
                    glyph = MarbleGlyph.SPARK,
                    enabled = !repo.busy && !repo.probeActive,
                    onClick = {
                        if (preview.profile == null) {
                            repo.pingAutoServerPool()
                        } else {
                            repo.runAutoServerSelection(reason = "manual", connect = false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SettingsIdentityLocationPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val (knownCount, totalCount) = repo.serverLocationSummary()

    MarbleSubPageScaffold(
        title = trx("Hide my real IP"),
        onBack = onBack
    ) {
        item(key = "identity-guard") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Identity Guard"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Identity Guard"),
                        subtitle = trx("Prevent accidental IP identity changes across reconnects"),
                        checked = s.identityGuardEnabled,
                        glyph = MarbleGlyph.SHIELD,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(identityGuardEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Strict Same-Exit Enforcement"),
                        subtitle = trx("Retry the same server before allowing failover to another exit"),
                        checked = s.identityGuardStrictNoFailover,
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.warning,
                        onCheckedChange = { repo.updateSettings(s.copy(identityGuardStrictNoFailover = it)) }
                    )
                }
            }
        }

        item(key = "location-intel") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Exit Intelligence & Country Flags"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Public Exit IP Intelligence"),
                        subtitle = trx("Resolve exit IP, country, ISP and ASN after connecting"),
                        checked = s.serverIntelEnabled,
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(serverIntelEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Auto-Detect Server Countries"),
                        subtitle = "$knownCount / $totalCount ${trx("servers resolved")}",
                        checked = s.serverLocationAutoDetect,
                        glyph = MarbleGlyph.RADAR,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(serverLocationAutoDetect = it)) }
                    )
                }
            }
        }
    }
}
