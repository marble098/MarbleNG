package com.marbleng.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.jitter.JitterAutoTuner
import com.marbleng.app.jitter.SmartJitterController
import com.marbleng.app.model.JitterSmartMode
import com.marbleng.app.model.SmartJitterDefaultsKeys

/**
 * MARBLE_SMART_JITTER_V238 — the Smart Jitter Reducer's page: the mode, the probe budget, the
 * switch rails, and the live readout the controller publishes from its own dispatcher.
 *
 * Persian is the lexicon (`trx`), the layout is the standard sub-page scaffold, and every value
 * shown comes from [AppRepository.jitterReducerState] — the page never measures anything itself.
 */
@Composable
fun SettingsSmartJitterPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val s = repo.settings
    val live by repo.jitterReducer.state.collectAsState()
    val mode = JitterSmartMode.fromId(s.jitterSmartMode)

    IosSubPageScaffold(
        title = trx("Smart Jitter Reducer"),
        onBack = onBack
    ) {
        item(key = "live") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Live route quality"))
                IosCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val stateLabel = when {
                            mode == JitterSmartMode.OFF -> trx("Module is off")
                            live.running -> trx("Measuring the live tunnel")
                            else -> trx("Waiting for a live tunnel")
                        }
                        Text(
                            text = stateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LiveStat(
                                label = trx("Jitter"),
                                value = if (live.samples > 0) "${live.jitterMs.toInt()} ms" else "—"
                            )
                            LiveStat(
                                label = trx("Mean RTT"),
                                value = if (live.samples > 0) "${live.rttMeanMs.toInt()} ms" else "—"
                            )
                            LiveStat(
                                label = trx("Loss"),
                                value = if (live.samples > 0) "${live.lossPercent.toInt()} %" else "—"
                            )
                            LiveStat(
                                label = trx("Score"),
                                value = live.currentScore?.let { "${it.toInt()}" } ?: "—"
                            )
                        }
                        if (live.series.size >= 2) {
                            JitterSparkline(series = live.series)
                            Text(
                                text = trx("Last two minutes"),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.tertiary
                            )
                        }
                        Text(
                            text = "${trx("Samples")}: ${live.validSamples} • " +
                                "${trx("Calm streak")}: ${live.calmStreak}" +
                                if (live.powerMultiplier > 1.0) {
                                    " • ${trx("Power saving: probing less")}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.tertiary
                        )
                    }
                }
            }
        }

        item(key = "switch-log") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Switch log"))
                IosCard {
                    if (live.switchLog.isEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                text = trx("No switches yet — the current route is holding"),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.tertiary
                            )
                        }
                    } else {
                        live.switchLog.take(6).forEachIndexed { index, entry ->
                            if (index > 0) IosDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "${entry.fromName.ifBlank { "—" }} → ${entry.toName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = entry.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Mode"))
                IosCard {
                    val modes = listOf(
                        Triple(JitterSmartMode.OFF, trx("Off"), trx("No probe, no switch, no lock")),
                        Triple(
                            JitterSmartMode.BALANCED,
                            trx("Balanced"),
                            trx("Adaptive probe; switches only on clear, repeated evidence")
                        ),
                        Triple(
                            JitterSmartMode.AGGRESSIVE,
                            trx("Aggressive (calls & games)"),
                            trx("Fast probe, sensitive trigger, low-latency Wi-Fi lock")
                        )
                    )
                    modes.forEachIndexed { index, (candidate, title, subtitle) ->
                        if (index > 0) IosDivider()
                        IosChoiceRow(
                            title = title,
                            subtitle = subtitle,
                            selected = mode == candidate,
                            glyph = MarbleGlyph.GAUGE,
                            squircleColor = when (candidate) {
                                JitterSmartMode.OFF -> colors.tertiary
                                JitterSmartMode.BALANCED -> colors.blue
                                JitterSmartMode.AGGRESSIVE -> colors.orange
                            },
                            onClick = {
                                repo.updateSettings(s.copy(smartJitter = s.smartJitter.copy(mode = candidate.id)))
                                repo.refreshJitterReducerTuning()
                            }
                        )
                    }
                }
            }
        }

        item(key = "probe") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Probe budget"))
                IosCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IosTextField(
                            label = trx("Probe endpoint (host)"),
                            value = s.jitterProbeHost,
                            monospace = true,
                            onValueChange = { raw ->
                                repo.updateSettings(
                                    s.copy(smartJitter = s.smartJitter.copy(probeHost = raw.trim().take(253)))
                                )
                            }
                        )
                        Text(
                            text = trx(
                                "A light HTTPS HEAD/204 request through the live tunnel — never ICMP, never user data"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.tertiary
                        )
                    }
                    IosDivider()
                    IosNumberStepperRow(
                        title = trx("Probe interval"),
                        subtitle = trx("Stretches automatically while the network is calm"),
                        value = s.jitterProbeIntervalSec,
                        range = SmartJitterDefaultsKeys.INTERVAL_SEC_MIN..SmartJitterDefaultsKeys.INTERVAL_SEC_MAX,
                        suffix = " s",
                        glyph = MarbleGlyph.GAUGE,
                        squircleColor = colors.cyan,
                        onValueChange = {
                            repo.updateSettings(s.copy(smartJitter = s.smartJitter.copy(probeIntervalSec = it)))
                            repo.refreshJitterReducerTuning()
                        }
                    )
                    IosDivider()
                    IosNumberStepperRow(
                        title = trx("Minimum samples"),
                        subtitle = trx("No decision before this many valid probes"),
                        value = s.jitterMinSamples,
                        range = SmartJitterDefaultsKeys.MIN_SAMPLES_MIN..SmartJitterDefaultsKeys.MIN_SAMPLES_MAX,
                        glyph = MarbleGlyph.VAULT,
                        squircleColor = colors.teal,
                        onValueChange = {
                            repo.updateSettings(s.copy(smartJitter = s.smartJitter.copy(minSamples = it)))
                            repo.refreshJitterReducerTuning()
                        }
                    )
                }
            }
        }

        item(key = "rails") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Switch guard rails"))
                IosCard {
                    IosNumberStepperRow(
                        title = trx("Minimum improvement"),
                        subtitle = trx("A challenger must beat the live route by this much"),
                        value = s.jitterSwitchImprovePercent,
                        range = SmartJitterDefaultsKeys.IMPROVE_PERCENT_MIN..SmartJitterDefaultsKeys.IMPROVE_PERCENT_MAX,
                        suffix = " %",
                        glyph = MarbleGlyph.SPARK,
                        squircleColor = colors.green,
                        onValueChange = {
                            repo.updateSettings(s.copy(smartJitter = s.smartJitter.copy(switchImprovePercent = it)))
                            repo.refreshJitterReducerTuning()
                        }
                    )
                    IosDivider()
                    IosNumberStepperRow(
                        title = trx("Switch cooldown"),
                        subtitle = trx("No two switches closer than this — no flapping"),
                        value = s.jitterSwitchCooldownSec,
                        range = SmartJitterDefaultsKeys.COOLDOWN_SEC_MIN..SmartJitterDefaultsKeys.COOLDOWN_SEC_MAX,
                        suffix = " s",
                        glyph = MarbleGlyph.POWER,
                        squircleColor = colors.indigo,
                        onValueChange = {
                            repo.updateSettings(s.copy(smartJitter = s.smartJitter.copy(switchCooldownSec = it)))
                            repo.refreshJitterReducerTuning()
                        }
                    )
                }
            }
        }

        item(key = "tuner") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Auto-Tuner (experimental)"))
                IosCard {
                    IosSwitchRow(
                        title = trx("MTU Auto-Tuner"),
                        subtitle = trx(
                            "Off by default. When on, a confirmed recommendation can be applied to the profile and is reversible."
                        ),
                        checked = s.jitterAutoTunerEnabled,
                        glyph = MarbleGlyph.TUNERS,
                        squircleColor = colors.purple,
                        onCheckedChange = {
                            repo.updateSettings(s.copy(smartJitter = s.smartJitter.copy(autoTunerEnabled = it)))
                        }
                    )
                    if (s.jitterAutoTunerEnabled) {
                        IosDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (s.jitterTunerMtu in JitterAutoTuner.MTU_MIN..JitterAutoTuner.MTU_MAX) {
                                    trx("Applied MTU override") + ": ${s.jitterTunerMtu}"
                                } else {
                                    trx("No MTU override applied — the adaptive policy decides")
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (s.jitterTunerMtu != 0) {
                                Text(
                                    text = trx("Set to 0 to return to the adaptive policy"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = IosTheme.colors.tertiary
        )
    }
}

/** The two-minute series as one calm bar chart — Canvas only, no chart dependency. */
@Composable
private fun JitterSparkline(series: List<Double>) {
    val accent = IosTheme.colors.accent
    val muted = IosTheme.colors.cardSecondary
    val samples = series.takeLast(SmartJitterController.SERIES_MAX)
    // The maximum is read before the draw scope: a @Composable value must not be read inside
    // Canvas (the compose-scope rule the project enforces).
    val peak = (samples.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (samples.size < 2) return@Canvas
        val barWidth = size.width / samples.size
        samples.forEachIndexed { index, value ->
            val fraction = (value / peak).coerceIn(0.0, 1.0)
            val barHeight = (size.height * fraction).toFloat()
            drawRoundRect(
                color = if (fraction >= 0.999) accent else muted,
                topLeft = Offset(x = index * barWidth + barWidth * 0.15f, y = size.height - barHeight),
                size = Size(width = barWidth * 0.7f, height = barHeight),
                cornerRadius = CornerRadius(x = barWidth * 0.35f, y = barWidth * 0.35f)
            )
        }
    }
}
