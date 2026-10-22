package com.marbleng.app.ui

// MARBLE_MERIDIAN_V221 — the flat chapter's two new pages.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.CensorshipLab
import com.marbleng.app.core.CensorshipProbe
import com.marbleng.app.core.ConnectWarmup
import com.marbleng.app.core.CorePower
import com.marbleng.app.core.FragmentPolicy
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.model.FragmentStrategy
import com.marbleng.app.model.PowerProfile
import com.marbleng.app.model.parseFlatAccent
import com.marbleng.app.model.parseFragmentStrategy
import com.marbleng.app.model.parsePowerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MARBLE_FLATLINE_V221 — the Interface page.
 *
 * Three decisions that used to be scattered — the surface material, the accent, and how hard the
 * cores may push this particular device — live on one page, each with a live preview beside the
 * choice. The page is deliberately short: the V213 critique's complaint was never that Settings had
 * too few options, it was that every option was equally loud.
 */
@Composable
internal fun MeridianInterfacePage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val settings = repo.settings
    val accent = parseFlatAccent(settings.accentTheme)
    val profile = parsePowerProfile(settings.powerProfile)
    val device = remember(repo.deviceCores, repo.deviceMemoryMb) {
        CorePower.Device(repo.deviceCores, repo.deviceMemoryMb)
    }
    var warmed by remember { mutableStateOf("") }
    LaunchedEffect(warmed) {
        if (warmed.isNotBlank()) {
            delay(2_400)
            warmed = ""
        }
    }

    SettingsSubPage(
        title = "Interface",
        subtitle = "Surfaces, accent and how hard the cores work",
        onBack = onBack,
        listState = listState
    ) {
        SettingsHubCard(
            title = "Surfaces",
            subtitle = "Flat is one tone and one hairline; classic keeps the lifted cards",
            tone = Aether.Cyan
        ) {
            SettingSwitch(
                title = "Flat surfaces",
                subtitle = "No shadows, no gradients: each box is a tonal step of the page",
                checked = settings.flatInterface
            ) {
                repo.updateSettings(repo.settings.copy(flatInterface = it))
            }
            SurfacePreview(flat = settings.flatInterface)
        }

        SettingsHubCard(
            title = "Accent",
            subtitle = "One colour reaches the tab, the ring, the slider and every selected row",
            tone = Aether.Amethyst
        ) {
            FlatAccent.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { option ->
                        MeridianAccentTile(
                            accent = option,
                            selected = option == accent,
                            modifier = Modifier.weight(1f)
                        ) {
                            repo.updateSettings(repo.settings.copy(accentTheme = option.id))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SettingsHubCard(
            title = "Power",
            subtitle = CorePower.summary(settings.powerProfile, device),
            tone = Aether.Emerald
        ) {
            PowerProfile.entries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { option ->
                        CyberSegment(
                            label = option.label,
                            detail = "",
                            selected = option == profile,
                            color = powerTone(option),
                            modifier = Modifier.weight(1f)
                        ) {
                            repo.updateSettings(repo.settings.copy(powerProfile = option.id))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                trx(CorePower.describe(settings.powerProfile)),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                trx("Sized for this device: ${device.coresSafe} cores, " +
                    (if (device.memoryMb > 0L) "${device.memoryMb / 1024L} GB" else "unknown memory")),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                trx("Takes effect on the next connect."),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall
            )
        }

        SettingsHubCard(
            title = "Connection warmup",
            subtitle = "Resolve the active route before the first ping asks for it",
            tone = Aether.Amber
        ) {
            SettingSwitch(
                title = "Warm the route on launch",
                subtitle = "One resolver round trip saved on the first connect and every ping",
                checked = settings.warmupOnLaunch
            ) {
                repo.updateSettings(repo.settings.copy(warmupOnLaunch = it))
            }
            PrismButton(
                label = if (warmed.isBlank()) "Warm now" else warmed,
                detail = "Clear and refill the cached resolutions",
                onClick = {
                    ConnectWarmup.clear()
                    repo.warmRoutes()
                    warmed = "Warming…"
                },
                tone = Aether.Amber,
                variant = PrismButtonVariant.Quiet,
                compact = true
            )
        }
    }
}

@Composable
private fun SurfacePreview(flat: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SurfacePreviewTile(flat = true, active = flat, modifier = Modifier.weight(1f))
        SurfacePreviewTile(flat = false, active = !flat, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SurfacePreviewTile(flat: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (flat) 12.dp else 16.dp)
    val fill = if (flat) Aether.Glass else Aether.VoidElevated
    val edge = if (active) Aether.Cyan.copy(alpha = .50f) else marbleNativeOutline()
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(if (flat) 1f else .72f)
                .height(6.dp)
                .clip(CircleShape)
                .background(Aether.Ink.copy(alpha = if (flat) .18f else .26f))
        )
        Box(
            Modifier
                .fillMaxWidth(if (flat) .66f else .48f)
                .height(5.dp)
                .clip(CircleShape)
                .background(Aether.Ink.copy(alpha = .10f))
        )
        Text(
            trx(if (flat) "Flat" else "Classic"),
            color = if (active) Aether.Cyan else Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MeridianAccentTile(
    accent: FlatAccent,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dot = accentSwatch(accent)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(if (selected) dot.copy(alpha = .12f) else Aether.Glass.copy(alpha = .55f))
            .border(1.dp, if (selected) dot.copy(alpha = .55f) else marbleNativeOutline(), shape)
            .tapSurface(onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(
            trx(accent.label),
            color = if (selected) dot else Aether.Ink,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The accent tile's tap target. The tile already draws its own selected/unselected fill and rim, so
 * it takes the platform's clickable without a second indication: two ripples stacked on one 64 dp
 * box is exactly the "imprecise under the thumb" feel the V213 critique named.
 */
private fun Modifier.tapSurface(onClick: () -> Unit): Modifier =
    this.clickable(role = Role.Button, onClick = onClick)

@Composable
private fun powerTone(profile: PowerProfile): Color = when (profile) {
    PowerProfile.EFFICIENT -> Aether.Amber
    PowerProfile.BALANCED -> Aether.Cyan
    PowerProfile.TURBO -> Aether.Emerald
    PowerProfile.EXTREME -> Aether.Danger
}

internal fun accentSwatch(accent: FlatAccent): Color = when (accent) {
    FlatAccent.OCEAN -> Color(0xFF0066CC)
    FlatAccent.VIOLET -> Color(0xFF7C5CE6)
    FlatAccent.EMERALD -> Color(0xFF0E9F6E)
    FlatAccent.AMBER -> Color(0xFFD97706)
    FlatAccent.ROSE -> Color(0xFFDB2777)
    FlatAccent.GRAPHITE -> Color(0xFF475569)
}

// ---------------------------------------------------------------------------------------------
// Censorship Lab
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_CENSORSHIP_LAB_V221 — the page that answers "why is nothing loading?".
 *
 * The scan runs the six-probe set off the main thread, classifies the evidence with pure rules, and
 * shows both the verdict and the prescription before either is applied. Applying is one tap, and it
 * only ever moves the knobs the lab owns — fragmentation, padding, fake-IP DNS and the address
 * family — never identity, routing or certificate policy.
 */
@Composable
internal fun MeridianShieldPage(
    repo: AppRepository,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val scope = rememberCoroutineScope()
    val settings = repo.settings
    var scanning by remember { mutableStateOf(false) }
    var diagnosis by remember { mutableStateOf<CensorshipLab.Diagnosis?>(null) }
    var status by remember { mutableStateOf("") }
    val fragment = parseFragmentStrategy(settings.fragmentStrategy)

    fun applyPrescription(target: CensorshipLab.Diagnosis, announce: Boolean) {
        val plan = CensorshipLab.prescribe(target, repo.settings)
        val next = plan.applyTo(repo.settings)
        repo.updateSettings(next)
        if (announce) {
            // The treatment is a sing-box-extended feature. Saying so at the moment it is applied is
            // the difference between a shield and a switch that silently does nothing.
            val needsSwitch = plan.touchesHandshake && repo.activeCoreEngine.id != "singbox"
            status = if (needsSwitch) {
                trx("Applied") + " • " +
                    trx("switch the engine to sing-box extended to use handshake fragmentation")
            } else {
                trx("Applied") + " • " + trx(plan.fragmentStrategy.label)
            }
        }
    }

    SettingsSubPage(
        title = "Censorship Lab",
        subtitle = "Measure what is blocking this path, then treat exactly that",
        onBack = onBack,
        listState = listState
    ) {
        SettingsHubCard(
            title = "Block scan",
            subtitle = "Five targets: names, literal addresses, two ports and the resolver",
            tone = Aether.Cyan
        ) {
            Text(
                trx(
                    if (scanning) "Probing this path…"
                    else "The scan takes a few seconds and never changes a setting by itself."
                ),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
            PrismButton(
                label = when {
                    scanning -> "Scanning…"
                    diagnosis == null -> "Run scan"
                    else -> "Scan again"
                },
                detail = "TCP, TLS and DNS on the live path",
                onClick = {
                    if (scanning) return@PrismButton
                    scanning = true
                    status = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { CensorshipProbe.scan(repo.settings) }
                                .getOrElse { error ->
                                    repo.diagnosticsEvent(
                                        "SHIELD",
                                        "scan-failed",
                                        "error" to (error.message ?: error.javaClass.simpleName)
                                    )
                                    CensorshipLab.diagnose(emptyList())
                                }
                        }
                        diagnosis = result
                        scanning = false
                        if (result.decisive && repo.settings.censorshipAutoApply) {
                            applyPrescription(result, announce = true)
                        } else {
                            status = result.findings
                                .map { trx(it.technique.label) }
                                .distinct()
                                .joinToString(" • ")
                                .ifBlank { trx(result.summary) }
                        }
                    }
                },
                tone = Aether.Cyan,
                variant = PrismButtonVariant.Primary,
                enabled = !scanning
            )
            if (status.isNotBlank()) {
                Text(
                    trx(status),
                    color = Aether.Ink,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        diagnosis?.let { result ->
            SettingsHubCard(
                title = "Verdict",
                subtitle = result.findings
                    .map { trx(it.technique.label) }
                    .distinct()
                    .joinToString(" • "),
                tone = when (result.severity) {
                    CensorshipLab.Severity.CRITICAL -> Aether.Danger
                    CensorshipLab.Severity.WARNING -> Aether.Amber
                    CensorshipLab.Severity.INFO -> Aether.Emerald
                }
            ) {
                if (result.findings.isEmpty()) {
                    Text(
                        trx("Nothing to treat: this path answered every probe."),
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                result.findings.forEach { finding ->
                    MeridianFindingRow(finding)
                }
                if (result.findings.isNotEmpty()) {
                    val plan = CensorshipLab.prescribe(result, settings)
                    PrismButton(
                        label = "Apply the prescribed shield",
                        detail = plan.notes.firstOrNull() ?: "One tap, reversible",
                        onClick = { applyPrescription(result, announce = true) },
                        tone = Aether.Emerald,
                        variant = PrismButtonVariant.Primary
                    )
                    plan.notes.drop(1).forEach { note ->
                        Text(
                            "• " + trx(note),
                            color = Aether.InkMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                MeridianObservationList(result.observations)
            }
        }

        SettingsHubCard(
            title = "Handshake treatment",
            subtitle = FragmentPolicy.describe(settings.fragmentStrategy),
            tone = Aether.Amethyst
        ) {
            FragmentStrategy.entries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { option ->
                        CyberSegment(
                            label = option.label,
                            detail = "",
                            selected = option == fragment,
                            color = Aether.Amethyst,
                            modifier = Modifier.weight(1f)
                        ) {
                            repo.updateSettings(repo.settings.copy(fragmentStrategy = option.id))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                if (repo.activeCoreEngine.id == "singbox") {
                    trx("Fragmentation runs on sing-box extended; Xray keeps its own socket treatment.")
                } else {
                    trx("The active engine is Xray: this treatment reaches the wire only after you " +
                        "switch to sing-box extended.")
                },
                color = if (repo.activeCoreEngine.id == "singbox") Aether.InkFaint else Aether.Amber,
                style = MaterialTheme.typography.bodySmall
            )
        }

        SettingsHubCard(
            title = "Automatic treatment",
            subtitle = "Let a decisive scan apply its own prescription",
            tone = Aether.Emerald
        ) {
            SettingSwitch(
                title = "Auto-apply a decisive verdict",
                subtitle = "Only when the scan names a blocking technique, never on a clean path",
                checked = settings.censorshipAutoApply
            ) {
                repo.updateSettings(repo.settings.copy(censorshipAutoApply = it))
            }
        }
    }
}

@Composable
private fun MeridianFindingRow(finding: CensorshipLab.Finding) {
    val tone = when (finding.severity) {
        CensorshipLab.Severity.CRITICAL -> Aether.Danger
        CensorshipLab.Severity.WARNING -> Aether.Amber
        CensorshipLab.Severity.INFO -> Aether.Cyan
    }
    MarbleNativeListItem(
        title = finding.technique.label,
        subtitle = finding.evidence,
        tone = tone,
        value = finding.severity.name.lowercase(),
        role = null
    )
}

@Composable
private fun MeridianObservationList(observations: List<CensorshipLab.Observation>) {
    if (observations.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            trx("Evidence"),
            color = Aether.Ink,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        observations.forEach { observation ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (observation.reachable) Aether.Emerald else Aether.Danger)
                )
                Text(
                    observation.target,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (observation.reachable) {
                        "${observation.latencyMs} ms"
                    } else {
                        observation.kind.name.lowercase()
                    },
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
