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

@Composable
fun SettingsPingTestingPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val s = repo.settings
    val engine = s.coreEngine()
    val homeAction = parseHomePingAction(s.homePingGaugeAction)

    IosSubPageScaffold(
        title = trx("Ping & Delay Testing"),
        onBack = onBack
    ) {
        item(key = "probe-methods") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Measurement Method"))
                IosCard {
                    val details = mapOf(
                        ProbeMethod.REAL_DELAY to (trx("Real HTTPS Delay") to trx("Opens an HTTPS request through the tunnel and measures browser-like latency")),
                        ProbeMethod.TCP_PING to (trx("TCP Handshake Ping") to trx("Fastest endpoint liveness check without starting a tunnel")),
                        ProbeMethod.URL_TEST to (trx("Core Native URL Test") to trx("Uses sing-box extended's built-in delay controller"))
                    )
                    ProbeMethod.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) IosDivider()
                        val available = candidate.availableOn(engine)
                        val pair = details.getValue(candidate)
                        IosChoiceRow(
                            title = pair.first,
                            subtitle = if (available) pair.second else trx(candidate.unavailableReason(engine)),
                            selected = s.probeMethod == candidate,
                            enabled = available,
                            glyph = MarbleGlyph.GAUGE,
                            squircleColor = when (candidate) {
                                ProbeMethod.REAL_DELAY -> colors.green
                                ProbeMethod.TCP_PING -> colors.cyan
                                ProbeMethod.URL_TEST -> colors.indigo
                            },
                            onClick = { repo.updateSettings(s.copy(probeMethod = candidate)) }
                        )
                    }
                }
            }
        }

        item(key = "probe-url-and-budgets") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("Test Endpoint & Budgets"))
                IosChipStrip {
                    listOf(
                        "Google 204" to DelayTest.URL,
                        "Cloudflare 204" to DelayTest.URL_SECONDARY,
                        "Firefox" to DelayTest.URL_TERTIARY
                    ).forEach { (label, url) ->
                        IosPillChip(
                            label = label,
                            selected = s.delayTestUrl.trim() == url,
                            onClick = { repo.updateSettings(s.copy(delayTestUrl = url)) }
                        )
                    }
                }
                IosCard(contentPadding = PaddingValues(16.dp)) {
                    IosTextField(
                        label = trx("Delay Test URL"),
                        value = s.delayTestUrl,
                        onValueChange = { repo.updateSettings(s.copy(delayTestUrl = it.trim())) }
                    )
                }
                IosCard {
                    IosNumberStepperRow(
                        title = trx("Ping Timeout"),
                        value = s.pingTimeoutSec,
                        range = 2..20,
                        suffix = "s",
                        glyph = MarbleGlyph.GAUGE,
                        squircleColor = colors.blue,
                        onValueChange = { repo.updateSettings(s.copy(pingTimeoutSec = it)) }
                    )
                    IosDivider()
                    IosNumberStepperRow(
                        title = trx("Samples per Server"),
                        value = s.pingSamples,
                        range = 1..6,
                        glyph = MarbleGlyph.LAYERS,
                        squircleColor = colors.indigo,
                        onValueChange = { repo.updateSettings(s.copy(pingSamples = it)) }
                    )
                    IosDivider()
                    IosNumberStepperRow(
                        title = trx("Parallel Sweep Workers"),
                        value = s.pingConcurrency,
                        range = 2..64,
                        step = 2,
                        glyph = MarbleGlyph.SPARK,
                        squircleColor = colors.orange,
                        onValueChange = { repo.updateSettings(s.copy(pingConcurrency = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Custom Sweep Speed"),
                        subtitle = if (s.pingSpeedCustom) {
                            "${PingSpeed.percent(s.pingSpeedPercent)}%"
                        } else {
                            trx("Default fast pace")
                        },
                        checked = s.pingSpeedCustom,
                        glyph = MarbleGlyph.TUNERS,
                        squircleColor = colors.teal,
                        onCheckedChange = { repo.updateSettings(s.copy(pingSpeedCustom = it)) }
                    )
                }

                if (s.pingSpeedCustom) {
                    IosCard(contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = trx("Sweep Pace"),
                                    color = colors.title,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${PingSpeed.percent(s.pingSpeedPercent)}%",
                                    color = colors.accent,
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
                                    thumbColor = colors.accent,
                                    activeTrackColor = colors.accent,
                                    inactiveTrackColor = colors.divider
                                )
                            )
                        }
                    }
                }
            }
        }

        item(key = "home-ping-action") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Home Screen Ping Action"))
                IosCard {
                    listOf(
                        HomePingAction.ROUTE to trx("Test Active / Selected Server"),
                        HomePingAction.GROUP to trx("Sweep Current Subscription Group"),
                        HomePingAction.LIBRARY to trx("Sweep Entire Server Library"),
                        HomePingAction.TESTS to trx("Open Ping Settings")
                    ).forEachIndexed { idx, (action, label) ->
                        if (idx > 0) IosDivider()
                        IosChoiceRow(
                            title = label,
                            selected = homeAction == action,
                            glyph = MarbleGlyph.HOME,
                            squircleColor = colors.green,
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
    val colors = IosTheme.colors
    val s = repo.settings
    val strategy = s.autoServerStrategyEnum
    val scope = s.autoServerScopeEnum
    val preview = repo.previewAutoServerChoice()

    IosSubPageScaffold(
        title = trx("Automatic Server Selector"),
        onBack = onBack
    ) {
        item(key = "auto-toggle-strategy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Selector & Strategy"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Enable Automatic Server Selector"),
                        subtitle = trx(AutoServerSelector.describe(strategy)),
                        checked = s.autoServerSelectorEnabled,
                        glyph = MarbleGlyph.RADAR,
                        squircleColor = colors.blue,
                        onCheckedChange = { repo.updateSettings(s.copy(autoServerSelectorEnabled = it)) }
                    )
                    AutoServerStrategy.entries.forEach { candidate ->
                        IosDivider()
                        IosChoiceRow(
                            title = trx(AutoServerSelector.shortLabel(candidate)),
                            subtitle = trx(AutoServerSelector.describe(candidate)),
                            selected = strategy == candidate,
                            glyph = MarbleGlyph.SPARK,
                            squircleColor = colors.indigo,
                            onClick = { repo.updateSettings(s.copy(autoServerStrategy = candidate.id)) }
                        )
                    }
                }
            }
        }

        if (strategy == AutoServerStrategy.CUSTOM) {
            item(key = "auto-custom-weights") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosSectionHeader(title = trx("Custom Signal Weights"))
                    IosCard {
                        IosNumberStepperRow(
                            title = trx("Latency Weight"),
                            value = s.autoServerPingWeight,
                            range = 0..100,
                            step = 5,
                            glyph = MarbleGlyph.GAUGE,
                            squircleColor = colors.blue,
                            onValueChange = { repo.updateSettings(s.copy(autoServerPingWeight = it)) }
                        )
                        IosDivider()
                        IosNumberStepperRow(
                            title = trx("Throughput / Load Weight"),
                            value = s.autoServerLoadWeight,
                            range = 0..100,
                            step = 5,
                            glyph = MarbleGlyph.DOWNLOAD,
                            squircleColor = colors.green,
                            onValueChange = { repo.updateSettings(s.copy(autoServerLoadWeight = it)) }
                        )
                        IosDivider()
                        IosNumberStepperRow(
                            title = trx("Stability Weight"),
                            value = s.autoServerStabilityWeight,
                            range = 0..100,
                            step = 5,
                            glyph = MarbleGlyph.SHIELD,
                            squircleColor = colors.indigo,
                            onValueChange = { repo.updateSettings(s.copy(autoServerStabilityWeight = it)) }
                        )
                        IosDivider()
                        IosNumberStepperRow(
                            title = trx("Freshness Weight"),
                            value = s.autoServerFreshnessWeight,
                            range = 0..100,
                            step = 5,
                            glyph = MarbleGlyph.REFRESH,
                            squircleColor = colors.orange,
                            onValueChange = { repo.updateSettings(s.copy(autoServerFreshnessWeight = it)) }
                        )
                    }
                }
            }
        }

        item(key = "auto-triggers") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Pool & Triggers"))
                IosSegmentedControl(
                    options = listOf(
                        AutoServerScope.SOURCE to trx("Current Source"),
                        AutoServerScope.ALL to trx("Entire Library")
                    ),
                    selected = scope,
                    onSelect = { repo.updateSettings(s.copy(autoServerScope = it.id)) }
                )
                IosCard {
                    IosSwitchRow(
                        title = trx("Select After Ping Sweep"),
                        checked = s.autoServerOnScan,
                        glyph = MarbleGlyph.CHECK,
                        squircleColor = colors.green,
                        onCheckedChange = { repo.updateSettings(s.copy(autoServerOnScan = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Choose When Pressing Connect"),
                        checked = s.autoServerOnConnect,
                        glyph = MarbleGlyph.POWER,
                        squircleColor = colors.blue,
                        onCheckedChange = { repo.updateSettings(s.copy(autoServerOnConnect = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Fail Over When Route Drops"),
                        checked = s.autoServerOnFailure,
                        glyph = MarbleGlyph.SHIELD,
                        squircleColor = colors.orange,
                        onCheckedChange = { repo.updateSettings(s.copy(autoServerOnFailure = it)) }
                    )
                    IosDivider()
                    IosNumberStepperRow(
                        title = trx("Switch Hysteresis Margin"),
                        value = s.autoServerSwitchMarginPercent,
                        range = 5..50,
                        step = 5,
                        suffix = "%",
                        glyph = MarbleGlyph.TUNERS,
                        squircleColor = colors.purple,
                        onValueChange = { repo.updateSettings(s.copy(autoServerSwitchMarginPercent = it)) }
                    )
                }
            }
        }

        item(key = "auto-preview") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("Current Recommendation"))
                IosCard {
                    IosListItem(
                        title = preview.profile?.let { cleanServerName(it.name) } ?: trx("Measure servers first"),
                        subtitle = trx(preview.reason),
                        glyph = MarbleGlyph.SPARK,
                        squircleColor = colors.accent,
                        showChevron = false,
                        trailing = {
                            preview.ranking.firstOrNull()?.let { best ->
                                IosBadge(
                                    text = "${(best.score * 100).roundToInt()}%",
                                    color = colors.accent
                                )
                            }
                        }
                    )
                }
                IosButton(
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
    val colors = IosTheme.colors
    val s = repo.settings
    val (knownCount, totalCount) = repo.serverLocationSummary()

    IosSubPageScaffold(
        title = trx("Identity Guard & Locations"),
        onBack = onBack
    ) {
        item(key = "identity-guard") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Identity Guard"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Identity Guard"),
                        subtitle = trx("Prevent accidental IP identity changes across reconnects"),
                        checked = s.identityGuardEnabled,
                        glyph = MarbleGlyph.SHIELD,
                        squircleColor = colors.indigo,
                        onCheckedChange = { repo.updateSettings(s.copy(identityGuardEnabled = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Strict Same-Exit Enforcement"),
                        subtitle = trx("Retry the same server before allowing failover to another exit"),
                        checked = s.identityGuardStrictNoFailover,
                        glyph = MarbleGlyph.CHECK,
                        squircleColor = colors.orange,
                        onCheckedChange = { repo.updateSettings(s.copy(identityGuardStrictNoFailover = it)) }
                    )
                }
            }
        }

        item(key = "location-intel") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Exit Intelligence & Country Flags"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Public Exit IP Intelligence"),
                        subtitle = trx("Resolve exit IP, country, ISP and ASN after connecting"),
                        checked = s.serverIntelEnabled,
                        glyph = MarbleGlyph.GLOBE,
                        squircleColor = colors.blue,
                        onCheckedChange = { repo.updateSettings(s.copy(serverIntelEnabled = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Auto-Detect Server Countries"),
                        subtitle = "$knownCount / $totalCount ${trx("servers resolved")}",
                        checked = s.serverLocationAutoDetect,
                        glyph = MarbleGlyph.RADAR,
                        squircleColor = colors.teal,
                        onCheckedChange = { repo.updateSettings(s.copy(serverLocationAutoDetect = it)) }
                    )
                }
            }
        }
    }
}
