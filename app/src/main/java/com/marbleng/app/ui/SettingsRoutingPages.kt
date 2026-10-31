package com.marbleng.app.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.AddressFamilyPolicy
import com.marbleng.app.core.CensorshipLab
import com.marbleng.app.core.CensorshipProbe
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.GeoAssetIndex
import com.marbleng.app.core.GeoAssetRegistry
import com.marbleng.app.core.IranPrecisionPack
import com.marbleng.app.core.RoutingEngine
import com.marbleng.app.core.RoutingOverview
import com.marbleng.app.core.RoutingPresets
import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.FragmentStrategy
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.IranModePolicy
import com.marbleng.app.model.PlainVoice
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind
import com.marbleng.app.model.SplitTunnelMode
import com.marbleng.app.model.parseFragmentStrategy
import com.marbleng.app.model.parseGeoPrecision
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

/**
 * MARBLE_ROUTING_REBUILD_V254 — the routing page, rebuilt from the outcome backwards.
 *
 * The complaint was that this page was crowded, noisy and meaningless. Reading it against the code
 * explained why: it was a rendering of the *storage*, not of the *decision*. Four independent
 * mechanisms wrote routing rules — the mode, the implicit geo lists, five "expert" comma-separated
 * text boxes that no screen offered, and a rule list behind its own switch — and the page showed
 * one of them at a time under headings that named the mechanism ("Rule Presets & Domain Strategy",
 * "GeoIP & GeoSite Databases", "Multi-Source Geo Databases"). A person could not answer the only
 * question they came with: *what happens to my traffic?*
 *
 * This page answers that question in the order a person asks it:
 *
 *  1. [what is sorted how](routing page, first card) — four modes, each one sentence, written
 *     through [RoutingOverview.decide] so the mode and the rule-list switch can never disagree;
 *  2. [which country lists power it](the geo cards) — one source picker, one precision dial, one
 *     update button, and the honest readiness of both files;
 *  3. [my own rules](the custom card) — one switch, then the rules themselves with pause, move,
 *     copy and delete, plus a visible warning for the two mistakes that used to be silent
 *     (duplicate matchers, regex matchers that cost on every request);
 *  4. [prove it](the simulator) — a host name in, the verdict and the winning step out.
 *
 * Everything that used to be a silent second control surface (the expert lists, the raw source
 * URLs) is still reachable, but behind one "Expert settings" row, and it says what it will do.
 */
@Composable
fun SettingsRoutingPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val assets = repo.routingAssetStatus()
    val rules = remember(s.routingRulesJson) { RoutingEngine.effectiveRules(s) }
    val geoIds = GeoAssetRegistry.parseIds(s.geoAssetSourceIds)
    val catalog = remember(s.geoAssetSourceIds, s.geoCustomSourcesJson) { GeoAssetRegistry.catalog(s) }
    val precision = parseGeoPrecision(s.geoPrecision)
    val sections = remember(s) { RoutingOverview.sections(s) }
    val duplicates = remember(rules) { RoutingOverview.duplicateMatchers(rules) }
    val regexRules = remember(rules) { RoutingOverview.regexRules(rules) }
    val activePreset = remember(rules) { RoutingOverview.activePresetId(s) }

    var editingRule by remember { mutableStateOf<RoutingRule?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var expertSheet by remember { mutableStateOf(false) }
    var simHost by rememberSaveable { mutableStateOf("") }
    var showAllSteps by rememberSaveable { mutableStateOf(false) }
    val simulation = remember(simHost, s) {
        if (simHost.isBlank()) null else repo.simulateRoute(simHost.trim())
    }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.ROUTE,
        tint = colors.success,
        title = trx("What goes direct"),
        subtitle = trx(RoutingOverview.summary(s.routingMode)),
        onBack = onBack
    ) {
        // ---------------------------------------------------------------- 1. the decision
        item(key = "routing-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("How traffic is sorted"))
                MarbleCard {
                    RoutingMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = trx(modeTitle(mode)),
                            subtitle = trx(RoutingOverview.summary(mode)),
                            selected = s.routingMode == mode,
                            glyph = modeGlyph(mode),
                            iconTint = mode.tone(colors),
                            onClick = { repo.setRoutingMode(mode) }
                        )
                    }
                }
                MarbleSectionFooter(trx(RoutingOverview.note(s.routingMode)))
            }
        }

        // ------------------------------------------------------- 2. what the decision reads
        item(key = "routing-layers") {
            if (sections.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSectionHeader(
                        title = trx("What this reads today"),
                        badge = faDigits("${sections.size}")
                    )
                    MarbleCard {
                        sections.forEachIndexed { index, section ->
                            if (index > 0) MarbleDivider()
                            val tone = section.outbound.tone(colors)
                            val count = faDigits("${section.count}")
                            MarbleListItem(
                                title = trx(section.title),
                                subtitle = trx(section.detail) + " • " + if (section.count == 1) {
                                    trx("One entry")
                                } else {
                                    trx("Entries") + ": " + count
                                },
                                glyph = when (section.outbound) {
                                    RoutingOutbound.DIRECT -> MarbleGlyph.HOME
                                    RoutingOutbound.BLOCK -> MarbleGlyph.SHIELD
                                    RoutingOutbound.PROXY -> MarbleGlyph.TUNNEL
                                },
                                iconTint = tone,
                                showChevron = false,
                                trailing = {
                                    MarbleBadge(
                                        text = trx(section.outbound.label),
                                        color = tone
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------- 3. the geo database
        item(key = "routing-lists-state") {
            val missing = buildList {
                if (!assets.geoIpReady && RoutingEngine.needsGeoIp(s)) add("Address list")
                if (!assets.geoSiteReady && RoutingEngine.needsGeoSite(s)) add("Site list")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Country lists"),
                    badge = if (missing.isEmpty()) trx("Ready") else trx("Needs download"),
                    badgeColor = if (missing.isEmpty()) colors.success else colors.warning
                )
                if (missing.isNotEmpty()) {
                    MarbleCard(contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (missing.size == 1) {
                                    trx("The country rules stay paused until this file is downloaded") +
                                        ": " + trx(missing.first())
                                } else {
                                    trx("The country rules stay paused until both files are downloaded") +
                                        ": " + missing.map { trx(it) }.joinToString(" + ")
                                },
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            MarbleButton(
                                label = trx("Download the lists"),
                                glyph = MarbleGlyph.DOWNLOAD,
                                enabled = !repo.busy && repo.state == "DISCONNECTED",
                                onClick = { repo.prepareRoutingAssets(force = true) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (repo.state != "DISCONNECTED") {
                                MarbleSectionFooter(
                                    trx("Disconnect first — a download cannot run inside an active tunnel.")
                                )
                            }
                        }
                    }
                }
                MarbleCard {
                    RoutingAssetRow(
                        title = trx("Address list"),
                        file = "geoip.dat",
                        ready = assets.geoIpReady,
                        bytes = assets.geoIpBytes,
                        updatedAt = assets.geoIpUpdatedAt
                    )
                    MarbleDivider()
                    RoutingAssetRow(
                        title = trx("Site list"),
                        file = "geosite.dat",
                        ready = assets.geoSiteReady,
                        bytes = assets.geoSiteBytes,
                        updatedAt = assets.geoSiteUpdatedAt
                    )
                }
            }
        }

        item(key = "routing-source") {
            val primary = geoIds.firstOrNull() ?: catalog.firstOrNull()?.id.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Where the lists come from"))
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MarbleDropdownField(
                            label = trx("Download source"),
                            value = primary,
                            options = catalog.map { spec ->
                                MarbleMenuOption(
                                    value = spec.id,
                                    label = spec.label,
                                    detail = if (spec.bundled) {
                                        "geoip.dat + geosite.dat • " + trx("Already inside the app")
                                    } else {
                                        "geoip.dat + geosite.dat"
                                    }
                                )
                            },
                            onSelect = { id ->
                                repo.applyGeoAssetSource(id)
                            }
                        )
                        // MARBLE_ROUTING_REBUILD_V254 — the dropdown already names the source, so
                        // this line says the one thing it cannot: the first source is the one the
                        // country lists are read from, and the rest are added to it.
                        Text(
                            text = if (s.geoMultiSourceEnabled) {
                                trx("Read first, then the ones switched on below.")
                            } else {
                                trx("Both files come from this address.")
                            },
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Combine several sources"),
                        subtitle = trx("Extra sources are read after the first one"),
                        checked = s.geoMultiSourceEnabled,
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = colors.secondary,
                        onCheckedChange = { on ->
                            repo.updateSettings(s.copy(geoMultiSourceEnabled = on))
                        }
                    )
                    if (s.geoMultiSourceEnabled) {
                        catalog.filterNot { it.id == primary }.forEach { spec ->
                            MarbleDivider()
                            val selected = spec.id in geoIds
                            MarbleSwitchRow(
                                title = spec.label,
                                subtitle = sourceDetail(spec),
                                checked = selected,
                                glyph = MarbleGlyph.FOLDER,
                                iconTint = if (selected) colors.tertiary else colors.onSurfaceVariant,
                                onCheckedChange = { repo.toggleGeoAssetSource(spec.id) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "routing-precision") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(
                    title = trx("How much domestic traffic stays direct"),
                    badge = faDigits("${domesticEntries(precision)}") + " " + trx("Entries"),
                    badgeColor = colors.primary
                )
                MarbleCard {
                    GeoPrecision.entries.forEachIndexed { index, level ->
                        if (index > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = trx(precisionTitle(level)),
                            subtitle = trx(precisionHint(level)),
                            selected = precision == level,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.success,
                            onClick = { repo.setGeoPrecision(level) }
                        )
                    }
                }
                if (!s.iranDomesticDirect) {
                    MarbleSectionFooter(
                        trx("This only works while Iranian sites stay direct is switched on.")
                    )
                }
            }
        }

        // ---------------------------------------------------------------- 4. own rules
        item(key = "routing-custom-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("My own rules"),
                    badge = faDigits("${rules.count { it.enabled }}") + " / " + faDigits("${rules.size}")
                )
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Use my own rules"),
                        subtitle = trx("Rules you wrote here"),
                        checked = s.customRoutingEnabled,
                        glyph = MarbleGlyph.TUNERS,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.setCustomRoutingEnabled(it) }
                    )
                }
            }
        }

        if (rules.isNotEmpty()) {
            item(key = "routing-custom-warnings") {
                val warnings = buildList {
                    if (duplicates.isNotEmpty()) {
                        add(
                            trx("Some rules repeat each other") + " • " +
                                faDigits("${duplicates.size}")
                        )
                    }
                    if (regexRules.isNotEmpty()) {
                        add(trx("Regular expressions cost time on every request") + " • " +
                            faDigits("${regexRules.size}"))
                    }
                }
                if (warnings.isNotEmpty()) {
                    MarbleCard(contentPadding = PaddingValues(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            warnings.forEach { line ->
                                Text(
                                    text = "• $line",
                                    color = colors.warning,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "routing-custom-list") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleCard {
                    MarbleListItem(
                        title = trx("Add a rule"),
                        subtitle = trx("Pick what to match, then where it goes"),
                        glyph = MarbleGlyph.PLUS,
                        iconTint = colors.primary,
                        onClick = {
                            editingIsNew = true
                            editingRule = RoutingRule(
                                id = UUID.randomUUID().toString().take(10),
                                enabled = true,
                                kind = RoutingRuleKind.DOMAIN,
                                matcher = "",
                                outbound = RoutingOutbound.PROXY
                            )
                        }
                    )
                    rules.forEachIndexed { index, rule ->
                        MarbleDivider()
                        val tone = rule.outbound.tone(colors)
                        val issue = RoutingEngine.validateRule(rule)
                            .firstOrNull { it.severity == RoutingEngine.IssueSeverity.ERROR }
                        val duplicateOf = duplicates[rule.id]
                        MarbleListItem(
                            title = rule.remark.ifBlank { rule.matcher.ifBlank { trx(rule.kind.label) } },
                            subtitle = buildString {
                                append(trx(rule.kind.label))
                                append(" · ")
                                append(rule.matcher)
                                if (issue != null) {
                                    append(" • ")
                                    append(trx("needs attention"))
                                } else if (duplicateOf != null) {
                                    append(" • ")
                                    append(trx("same as an earlier rule"))
                                } else if (!rule.enabled) {
                                    append(" • ")
                                    append(trx("paused"))
                                }
                            },
                            glyph = when (rule.outbound) {
                                RoutingOutbound.PROXY -> MarbleGlyph.TUNNEL
                                RoutingOutbound.DIRECT -> MarbleGlyph.HOME
                                RoutingOutbound.BLOCK -> MarbleGlyph.SHIELD
                            },
                            iconTint = if (rule.enabled) tone else colors.onSurfaceVariant,
                            titleColor = if (rule.enabled) colors.onSurface else colors.onSurfaceVariant,
                            showChevron = false,
                            onClick = {
                                editingIsNew = false
                                editingRule = rule
                            },
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    MarbleBadge(text = trx(rule.outbound.label), color = tone)
                                    MarbleIconButton(
                                        glyph = MarbleGlyph.CHEVRON_DOWN,
                                        onClick = { repo.moveRoutingRule(rule.id, -1) },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .rotate(180f),
                                        tint = colors.onSurfaceVariant,
                                        enabled = index > 0,
                                        contentDescription = trx("Move up")
                                    )
                                    MarbleIconButton(
                                        glyph = MarbleGlyph.CHEVRON_DOWN,
                                        onClick = { repo.moveRoutingRule(rule.id, 1) },
                                        modifier = Modifier.size(34.dp),
                                        tint = colors.onSurfaceVariant,
                                        enabled = index < rules.lastIndex,
                                        contentDescription = trx("Move down")
                                    )
                                }
                            }
                        )
                    }
                }
                if (rules.isEmpty()) {
                    MarbleSectionFooter(
                        trx("No rules yet. The mode above still decides everything.")
                    )
                }
            }
        }

        item(key = "routing-presets") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Start from a ready set"))
                MarbleChipStrip {
                    RoutingPresets.Preset.entries.forEach { preset ->
                        MarblePillChip(
                            label = trx(preset.title) + " · " + faDigits("${preset.rules.size}"),
                            selected = activePreset == preset.id,
                            accent = colors.success,
                            onClick = { repo.applyRoutingPreset(preset) }
                        )
                    }
                }
                val active = RoutingPresets.Preset.entries.firstOrNull { it.id == activePreset }
                MarbleSectionFooter(
                    trx("A ready set replaces the list and switches my own rules on.") + " " +
                        trx(active?.summary.orEmpty())
                )
            }
        }

        // ------------------------------------------------------------- 5. prove the route
        item(key = "routing-simulator") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Try a site"))
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MarbleTextField(
                            label = trx("Site or address"),
                            value = simHost,
                            onValueChange = { simHost = it },
                            placeholder = "digikala.com"
                        )
                        MarbleChipStrip {
                            listOf("digikala.com", "google.com", "1.1.1.1").forEach { sample ->
                                MarblePillChip(
                                    label = sample,
                                    selected = simHost == sample,
                                    accent = colors.primary,
                                    onClick = { simHost = sample }
                                )
                            }
                        }
                        simulation?.let { sim ->
                            val tone = sim.verdict.tone(colors)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MarbleBadge(text = trx(sim.verdict.label), color = tone)
                                    Text(
                                        text = trx(sim.verdictReason),
                                        color = colors.onSurface,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                val steps = if (showAllSteps) sim.steps else sim.steps.filter { it.matched == true }
                                steps.forEachIndexed { index, step ->
                                    if (index > 0) MarbleDivider()
                                    val stepTone = step.outbound?.tone(colors) ?: colors.onSurfaceVariant
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MarbleBadge(
                                            text = if (step.skipped) trx("Skipped")
                                            else if (step.matched == null) trx("Checked later")
                                            else if (step.matched == true) trx("Matched")
                                            else trx("No match"),
                                            color = if (step.matched == true) stepTone else colors.onSurfaceVariant
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = trx(step.title),
                                                color = colors.onSurface,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Text(
                                                text = step.detail,
                                                color = colors.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            )
                                        }
                                    }
                                }
                                MarbleButton(
                                    label = if (showAllSteps) trx("Show only what matched")
                                    else trx("Show every step"),
                                    style = MarbleButtonStyle.SECONDARY,
                                    compact = true,
                                    onClick = { showAllSteps = !showAllSteps }
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "routing-tools") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Housekeeping"))
                MarbleCard {
                    MarbleListItem(
                        title = trx("Update the lists now"),
                        subtitle = trx("Fetch both files again from the source above"),
                        glyph = MarbleGlyph.REFRESH,
                        iconTint = colors.primary,
                        enabled = !repo.busy,
                        onClick = { repo.prepareRoutingAssets(force = true) }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Check the policy with the core"),
                        subtitle = trx("Builds a real config and asks the core whether it runs"),
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.success,
                        enabled = !repo.busy,
                        onClick = { repo.verifyRoutingPolicy() }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Expert settings"),
                        subtitle = trx("Custom download addresses and the old comma-separated lists"),
                        glyph = MarbleGlyph.TUNERS,
                        iconTint = colors.tertiary,
                        onClick = { expertSheet = true }
                    )
                }
            }
        }
    }

    editingRule?.let { current ->
        RoutingRuleEditSheet(
            repo = repo,
            initial = current,
            isNew = editingIsNew,
            duplicatesOf = duplicates[current.id],
            onDismiss = { editingRule = null }
        )
    }

    if (expertSheet) {
        RoutingExpertSheet(repo = repo, onDismiss = { expertSheet = false })
    }
}

/** The four modes, named for the outcome rather than for the mechanism. */
private fun modeTitle(mode: RoutingMode): String = when (mode) {
    RoutingMode.PROXY_ALL -> "Everything through the tunnel"
    RoutingMode.BYPASS_PRIVATE -> "Keep the home network direct"
    RoutingMode.GEO_DIRECT -> "Iranian sites stay direct"
    RoutingMode.CUSTOM -> "Only my own rules"
}

private fun modeGlyph(mode: RoutingMode): MarbleGlyph = when (mode) {
    RoutingMode.PROXY_ALL -> MarbleGlyph.GLOBE
    RoutingMode.BYPASS_PRIVATE -> MarbleGlyph.HOME
    RoutingMode.GEO_DIRECT -> MarbleGlyph.SHIELD
    RoutingMode.CUSTOM -> MarbleGlyph.TUNERS
}

/** Tone per mode; one place, so the four rows read as one family. */
@Composable
@ReadOnlyComposable
private fun RoutingMode.tone(colors: ColorScheme): Color = when (this) {
    RoutingMode.PROXY_ALL -> colors.primary
    RoutingMode.BYPASS_PRIVATE -> colors.tertiary
    RoutingMode.GEO_DIRECT -> colors.success
    RoutingMode.CUSTOM -> colors.secondary
}

@Composable
@ReadOnlyComposable
private fun RoutingOutbound.tone(colors: ColorScheme): Color = when (this) {
    RoutingOutbound.PROXY -> colors.primary
    RoutingOutbound.DIRECT -> colors.success
    RoutingOutbound.BLOCK -> colors.error
}

private fun precisionTitle(level: GeoPrecision): String = when (level) {
    GeoPrecision.STANDARD -> "Country lists only"
    GeoPrecision.ENHANCED -> "Country lists plus known domestic sites"
    GeoPrecision.MAXIMUM -> "Also match brand names"
}

/** How many entries this level adds on top of the downloaded list. */
private fun domesticEntries(level: GeoPrecision): Int =
    IranPrecisionPack.domains(level).size + IranPrecisionPack.keywords(level).size

/** A plain sentence per level; the badge above already names the count. */
private fun precisionHint(level: GeoPrecision): String = when (level) {
    GeoPrecision.STANDARD ->
        "Uses only what the downloaded lists call Iran. Nothing is added locally."
    GeoPrecision.ENHANCED ->
        "Also keeps the .ir suffix and a curated set of domestic banks, shops and messengers direct."
    GeoPrecision.MAXIMUM ->
        "Also matches well-known domestic brand names inside longer addresses. Widest reach, and the one to pick when a site still takes the tunnel."
}

/**
 * MARBLE_ROUTING_REBUILD_V254 — what one source actually brings, and nothing else.
 *
 * This row used to print `On · geoip.dat + geosite.dat · 3 / 6` on every line: the on/off that the
 * switch already says, and a slot counter that is storage accounting, not a decision. What is left
 * is the only fact that changes what the row means — which of the two files this source can supply.
 */
@Composable
@ReadOnlyComposable
private fun sourceDetail(spec: GeoAssetRegistry.SourceSpec): String {
    val files = buildList {
        if (spec.provides(GeoAssetRegistry.Kind.GEOIP)) add("geoip.dat")
        if (spec.provides(GeoAssetRegistry.Kind.GEOSITE)) add("geosite.dat")
    }
    return if (files.isEmpty()) trx("Nothing to download") else files.joinToString(" + ")
}

@Composable
private fun RoutingAssetRow(
    title: String,
    file: String,
    ready: Boolean,
    bytes: Long,
    updatedAt: Long
) {
    val colors = MaterialTheme.colorScheme
    MarbleListItem(
        title = title,
        subtitle = if (ready) {
            file + " • " + faDigits(formatBytes(bytes)) + " • " + trx(relativeTimeLabel(updatedAt))
        } else {
            file + " • " + trx("not downloaded yet")
        },
        glyph = if (ready) MarbleGlyph.CHECK else MarbleGlyph.DOWNLOAD,
        iconTint = if (ready) colors.success else colors.warning,
        showChevron = false
    )
}

/**
 * MARBLE_ROUTING_REBUILD_V254 — the rule editor, with the one thing the old sheet never had: ideas.
 *
 * A rule that matches a country list is written as a *tag*, and the tag has to exist in the file
 * that is actually downloaded. The old sheet was a free-text box and a note that the tag "is not in
 * the loaded geosite.dat", which is a dead end. The index already knows every tag in every file, so
 * the sheet now offers the matches as chips while the person types — for both countries and sites —
 * and shows where a tag came from.
 */
@Composable
private fun RoutingRuleEditSheet(
    repo: AppRepository,
    initial: RoutingRule,
    isNew: Boolean,
    duplicatesOf: String?,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val issues = remember(draft) { RoutingEngine.validateRule(draft) }
    val hasErrors = issues.any { it.severity == RoutingEngine.IssueSeverity.ERROR }
    val suggestions = remember(draft.kind, draft.matcher) {
        when (draft.kind) {
            RoutingRuleKind.GEOSITE ->
                GeoAssetIndex.suggest(GeoAssetIndex.Kind.GEOSITE, draft.matcher, limit = 6)
            RoutingRuleKind.GEOIP ->
                GeoAssetIndex.suggest(GeoAssetIndex.Kind.GEOIP, draft.matcher, limit = 6)
            else -> emptyList()
        }
    }

    MarbleBottomSheet(
        title = if (isNew) trx("New rule") else trx("Edit rule"),
        subtitle = trx(ruleKindHint(draft.kind)),
        onDismiss = onDismiss,
        scrollable = true
    ) {
        MarbleDropdownField(
            label = trx("What it matches"),
            value = draft.kind.name,
            options = RoutingRuleKind.entries.map { kind ->
                MarbleMenuOption(kind.name, trx(kind.label), trx(ruleKindHint(kind)))
            },
            onSelect = { name ->
                val picked = RoutingRuleKind.entries.firstOrNull { it.name == name } ?: return@MarbleDropdownField
                draft = draft.copy(kind = picked, matcher = "")
            }
        )

        MarbleTextField(
            label = trx(matcherLabel(draft.kind)),
            value = draft.matcher,
            onValueChange = { draft = draft.copy(matcher = it) },
            placeholder = matcherPlaceholder(draft.kind),
            singleLine = draft.kind != RoutingRuleKind.IP && draft.kind != RoutingRuleKind.DOMAIN,
            minLines = if (draft.kind == RoutingRuleKind.IP || draft.kind == RoutingRuleKind.DOMAIN) 2 else 1,
            maxLines = 4,
            isError = hasErrors
        )

        if (suggestions.isNotEmpty()) {
            MarbleChipStrip {
                suggestions.forEach { entry ->
                    MarblePillChip(
                        label = entry.tag + " · " + faDigits("${entry.count}"),
                        selected = draft.matcher.trim().equals(entry.tag, ignoreCase = true),
                        accent = colors.tertiary,
                        onClick = { draft = draft.copy(matcher = entry.tag) }
                    )
                }
            }
        }

        MarbleTextField(
            label = trx("Name (optional)"),
            value = draft.remark,
            onValueChange = { draft = draft.copy(remark = it) }
        )

        MarbleSegmentedControl(
            options = listOf(
                RoutingOutbound.PROXY to trx(RoutingOutbound.PROXY.label),
                RoutingOutbound.DIRECT to trx(RoutingOutbound.DIRECT.label),
                RoutingOutbound.BLOCK to trx(RoutingOutbound.BLOCK.label)
            ),
            selected = draft.outbound,
            onSelect = { draft = draft.copy(outbound = it) }
        )

        if (draft.kind != RoutingRuleKind.PORT) {
            MarbleTextField(
                label = trx("Only these ports (optional)"),
                value = draft.port,
                onValueChange = { draft = draft.copy(port = it) },
                placeholder = "443, 8443 or 1000-2000"
            )
            MarbleSwitchRow(
                title = trx("Apply to both TCP and UDP"),
                subtitle = trx("Switch off to limit this rule to one transport"),
                checked = draft.network.isBlank(),
                glyph = MarbleGlyph.LAYERS,
                iconTint = colors.secondary,
                onCheckedChange = { both ->
                    draft = draft.copy(network = if (both) "" else "tcp")
                }
            )
        }

        issues.forEach { issue ->
            Text(
                text = trx(issue.message),
                color = if (issue.severity == RoutingEngine.IssueSeverity.ERROR) colors.error else colors.warning,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (duplicatesOf != null) {
            Text(
                text = trx("An earlier rule already matches this the same way"),
                color = colors.warning,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MarbleButton(
                label = if (isNew) trx("Add rule") else trx("Save"),
                enabled = !hasErrors && draft.matcher.isNotBlank(),
                onClick = {
                    val current = RoutingEngine.effectiveRules(repo.settings)
                    val next = if (isNew) {
                        current + draft
                    } else {
                        current.map { if (it.id == initial.id) draft else it }
                    }
                    repo.setRoutingRules(next)
                    if (isNew) repo.setCustomRoutingEnabled(true)
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
            if (!isNew) {
                MarbleButton(
                    label = trx("Delete"),
                    style = MarbleButtonStyle.DESTRUCTIVE,
                    onClick = {
                        repo.setRoutingRules(
                            RoutingEngine.effectiveRules(repo.settings).filterNot { it.id == initial.id }
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (!isNew) {
            MarbleSwitchRow(
                title = trx("This rule is on"),
                checked = draft.enabled,
                glyph = MarbleGlyph.CHECK,
                iconTint = colors.success,
                onCheckedChange = { draft = draft.copy(enabled = it) }
            )
        }
    }
}

private fun matcherLabel(kind: RoutingRuleKind): String = when (kind) {
    RoutingRuleKind.GEOSITE -> "Country or site list name"
    RoutingRuleKind.GEOIP -> "Country list name"
    RoutingRuleKind.DOMAIN -> "Domains"
    RoutingRuleKind.IP -> "Addresses or ranges"
    RoutingRuleKind.PORT -> "Ports"
}

private fun matcherPlaceholder(kind: RoutingRuleKind): String = when (kind) {
    RoutingRuleKind.GEOSITE -> "ir, category-ads-all"
    RoutingRuleKind.GEOIP -> "ir, cn or private"
    RoutingRuleKind.DOMAIN -> "example.com, domain:shop.ir"
    RoutingRuleKind.IP -> "1.1.1.1, 10.0.0.0/8"
    RoutingRuleKind.PORT -> "443, 80,8443"
}

private fun ruleKindHint(kind: RoutingRuleKind): String = when (kind) {
    RoutingRuleKind.GEOSITE -> "A group of sites already named inside the downloaded site list"
    RoutingRuleKind.GEOIP -> "A country's addresses, answered by the downloaded address list"
    RoutingRuleKind.DOMAIN -> "Addresses you type yourself, one per line"
    RoutingRuleKind.IP -> "Single addresses, ranges or CIDR blocks"
    RoutingRuleKind.PORT -> "A port or a port range, whatever the destination is"
}

/**
 * MARBLE_ROUTING_REBUILD_V254 — the expert surface, now an opt-in.
 *
 * These five text boxes were emitted by the core (`routeDirectDomains`, `routeProxyDomains`,
 * `routeBlockDomains`, `routeDirectIps`, `routeBlockIps`) but no screen in Settings offered them, so
 * their only user was a restored backup. They are shown here, together, with a plain sentence about
 * what each list does and what format it takes, instead of being either invisible or a fifth
 * heading on a page that was already too long.
 */
@Composable
private fun RoutingExpertSheet(
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings

    fun write(
        directDomains: String = s.routeDirectDomains,
        proxyDomains: String = s.routeProxyDomains,
        blockDomains: String = s.routeBlockDomains,
        directIps: String = s.routeDirectIps,
        blockIps: String = s.routeBlockIps,
        geoIpUrl: String = s.geoIpUrl,
        geoSiteUrl: String = s.geoSiteUrl
    ) {
        repo.updateSettings(
            s.copy(
                routeDirectDomains = directDomains,
                routeProxyDomains = proxyDomains,
                routeBlockDomains = blockDomains,
                routeDirectIps = directIps,
                routeBlockIps = blockIps,
                geoIpUrl = geoIpUrl,
                geoSiteUrl = geoSiteUrl
            )
        )
    }

    MarbleBottomSheet(
        title = trx("Expert settings"),
        subtitle = trx("Everything here is optional and adds to the mode above."),
        onDismiss = onDismiss,
        scrollable = true
    ) {
        Text(
            text = trx("One entry per line or separated by commas. Domains take domain:, full:, keyword: or regexp: in front. Addresses take a plain IP, a range or CIDR."),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        MarbleTextField(
            label = trx("Always direct sites"),
            value = s.routeDirectDomains,
            onValueChange = { write(directDomains = it) },
            singleLine = false,
            minLines = 2,
            maxLines = 6
        )
        MarbleTextField(
            label = trx("Always direct addresses"),
            value = s.routeDirectIps,
            onValueChange = { write(directIps = it) },
            singleLine = false,
            minLines = 2,
            maxLines = 6
        )
        MarbleTextField(
            label = trx("Always through the tunnel"),
            value = s.routeProxyDomains,
            onValueChange = { write(proxyDomains = it) },
            singleLine = false,
            minLines = 2,
            maxLines = 6
        )
        MarbleTextField(
            label = trx("Always blocked sites"),
            value = s.routeBlockDomains,
            onValueChange = { write(blockDomains = it) },
            singleLine = false,
            minLines = 2,
            maxLines = 6
        )
        MarbleTextField(
            label = trx("Always blocked addresses"),
            value = s.routeBlockIps,
            onValueChange = { write(blockIps = it) },
            singleLine = false,
            minLines = 2,
            maxLines = 6
        )

        MarbleTextField(
            label = trx("Custom address list download"),
            value = s.geoIpUrl,
            onValueChange = { write(geoIpUrl = it.trim()) },
            placeholder = "https://…/geoip.dat"
        )
        MarbleTextField(
            label = trx("Custom site list download"),
            value = s.geoSiteUrl,
            onValueChange = { write(geoSiteUrl = it.trim()) },
            placeholder = "https://…/geosite.dat"
        )

        MarbleSectionFooter(
            trx("Only https:// downloads are accepted. A download cannot run while the tunnel is connected.")
        )
        MarbleButton(
            label = trx("Done"),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsDnsAndFamilyPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val underlay = repo.networkSnapshot
    val plan = AddressFamilyPolicy.plan(settings = s, underlayHasIpv6 = underlay.hasIpv6)
    var editingDoH by rememberSaveable { mutableStateOf(false) }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.GLOBE,
        tint = colors.tertiary,
        title = trx("DNS and IP version"),
        subtitle = AddressFamilyPolicy.describe(plan),
        onBack = onBack
    ) {
        item(key = "family-policy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Which address version is used"))
                MarbleCard {
                    AddressFamilyMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = trx(familyTitle(mode)),
                            subtitle = trx(familyHint(mode)),
                            selected = s.addressFamilyMode == mode,
                            glyph = MarbleGlyph.GLOBE,
                            iconTint = colors.tertiary,
                            onClick = {
                                repo.updateSettings(
                                    s.copy(
                                        addressFamilyMode = mode,
                                        ipv6Enabled = mode != AddressFamilyMode.FORCE_IPV4,
                                        preferIpv6 = mode in setOf(
                                            AddressFamilyMode.SMART,
                                            AddressFamilyMode.PREFER_IPV6,
                                            AddressFamilyMode.FORCE_IPV6
                                        ),
                                        dnsQueryStrategy = when (mode) {
                                            AddressFamilyMode.FORCE_IPV4 -> "UseIPv4"
                                            AddressFamilyMode.FORCE_IPV6 -> "UseIPv6"
                                            else -> "UseIP"
                                        }
                                    )
                                )
                            }
                        )
                    }
                    if (s.addressFamilyMode in setOf(AddressFamilyMode.FORCE_IPV4, AddressFamilyMode.FORCE_IPV6) ||
                        s.strictAddressFamily
                    ) {
                        MarbleDivider()
                        MarbleSwitchRow(
                            title = trx("Refuse servers that cannot use it"),
                            subtitle = trx("A server with no matching address is skipped instead of retried"),
                            checked = s.strictAddressFamily,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.warning,
                            onCheckedChange = { repo.updateSettings(s.copy(strictAddressFamily = it)) }
                        )
                    }
                }
                MarbleSectionFooter(
                    "${underlay.label} · " + trx("answers with") + " " +
                        trx(PlainVoice.dnsStrategyLabel(plan.dnsQueryStrategy))
                )
            }
        }

        item(key = "dns-resolvers") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Encrypted DNS"))
                MarbleCard {
                    listOf(
                        Triple("Cloudflare", "1.1.1.1" to "1.0.0.1", "https://1.1.1.1/dns-query" to "https://1.0.0.1/dns-query"),
                        Triple("Google", "8.8.8.8" to "8.8.4.4", "https://8.8.8.8/dns-query" to "https://8.8.4.4/dns-query"),
                        Triple("Quad9", "9.9.9.9" to "149.112.112.112", "https://9.9.9.9/dns-query" to "https://149.112.112.112/dns-query")
                    ).forEachIndexed { index, (name, ips, dohs) ->
                        if (index > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = name,
                            subtitle = ips.first + " · " + ips.second,
                            selected = s.dnsPrimaryIp == ips.first,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.success,
                            onClick = {
                                repo.updateSettings(
                                    s.copy(
                                        dnsPrimaryIp = ips.first,
                                        dnsSecondaryIp = ips.second,
                                        dnsPrimaryDoH = dohs.first,
                                        dnsSecondaryDoH = dohs.second
                                    )
                                )
                            }
                        )
                    }
                }
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("My own resolver"),
                        subtitle = "${s.dnsPrimaryIp} · ${s.dnsSecondaryIp}",
                        checked = editingDoH,
                        glyph = MarbleGlyph.TUNERS,
                        iconTint = colors.tertiary,
                        onCheckedChange = { editingDoH = it }
                    )
                    if (editingDoH) {
                        MarbleDivider()
                        MarbleTextField(
                            label = trx("First resolver"),
                            value = s.dnsPrimaryIp,
                            onValueChange = { repo.updateSettings(s.copy(dnsPrimaryIp = it.trim())) }
                        )
                        MarbleDivider()
                        MarbleTextField(
                            label = trx("Second resolver"),
                            value = s.dnsSecondaryIp,
                            onValueChange = { repo.updateSettings(s.copy(dnsSecondaryIp = it.trim())) }
                        )
                        MarbleDivider()
                        MarbleTextField(
                            label = trx("First encrypted URL"),
                            value = s.dnsPrimaryDoH,
                            onValueChange = { repo.updateSettings(s.copy(dnsPrimaryDoH = it.trim())) }
                        )
                        MarbleDivider()
                        MarbleTextField(
                            label = trx("Second encrypted URL"),
                            value = s.dnsSecondaryDoH,
                            onValueChange = { repo.updateSettings(s.copy(dnsSecondaryDoH = it.trim())) }
                        )
                    }
                }
            }
        }

        item(key = "dns-switches") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("DNS protection"))
                MarbleCard {
                    if (s.connectionMode != ConnectionMode.FULL_TUN || !s.dnsHijackEnabled) {
                        MarbleSwitchRow(
                            title = trx("Catch plain DNS inside the tunnel"),
                            subtitle = trx("Plain queries are answered by the resolver above instead of the network"),
                            checked = s.dnsHijackEnabled,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.success,
                            onCheckedChange = { repo.updateSettings(s.copy(dnsHijackEnabled = it)) }
                        )
                        MarbleDivider()
                    }
                    MarbleSwitchRow(
                        title = trx("Instant synthetic addresses"),
                        subtitle = trx("Answers immediately and resolves the real address inside the tunnel"),
                        checked = s.dnsFakeIpEnabled,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(dnsFakeIpEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Prefer the fastest resolver"),
                        subtitle = trx("Keeps the encrypted endpoints that answered best on this network"),
                        checked = s.adaptiveDnsEnabled,
                        glyph = MarbleGlyph.GAUGE,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(adaptiveDnsEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Match the address version to this network"),
                        subtitle = trx("Asks for the address family the link can actually carry"),
                        checked = s.adaptiveDualStackEnabled,
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(adaptiveDualStackEnabled = it)) }
                    )
                }
            }
        }
    }
}

private fun familyTitle(mode: AddressFamilyMode): String = when (mode) {
    AddressFamilyMode.SMART -> "Automatic"
    AddressFamilyMode.PREFER_IPV4 -> "IPv4 first"
    AddressFamilyMode.PREFER_IPV6 -> "IPv6 first"
    AddressFamilyMode.FORCE_IPV4 -> "IPv4 only"
    AddressFamilyMode.FORCE_IPV6 -> "IPv6 only"
}

private fun familyHint(mode: AddressFamilyMode): String = when (mode) {
    AddressFamilyMode.SMART -> "Uses what this network really carries, and falls back when it fails"
    AddressFamilyMode.PREFER_IPV4 -> "Tries IPv4 first, keeps IPv6 as the second choice"
    AddressFamilyMode.PREFER_IPV6 -> "Tries IPv6 first, keeps IPv4 as the second choice"
    AddressFamilyMode.FORCE_IPV4 -> "IPv6 is switched off; a server without IPv4 is refused"
    AddressFamilyMode.FORCE_IPV6 -> "IPv4 is switched off; a server without IPv6 is refused"
}

private data class InstalledAppRow(
    val packageName: String,
    val label: String
)

/**
 * MARBLE_ROUTING_REBUILD_V254 — the per-app page says who is in *and* who is out, at the top.
 *
 * The old page listed every installed app alphabetically with a switch and no memory of what had
 * been chosen, so the one question it exists for — "is this app inside?" — needed scrolling and
 * remembering. Chosen apps now float to a summary card, the rest follow under a search line, and an
 * empty selection says what it means instead of looking like a bug.
 */
@Composable
fun SettingsSplitTunnelPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val s = repo.settings
    var query by rememberSaveable { mutableStateOf("") }
    var installed by remember { mutableStateOf<List<InstalledAppRow>>(emptyList()) }

    val selectedPackages = remember(s.splitTunnelPackages) {
        s.splitTunnelPackages
            .split(',', '\n', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    LaunchedEffect(context) {
        installed = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            runCatching {
                pm.getInstalledApplications(0)
                    .filter { app ->
                        app.packageName != context.packageName &&
                            ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                                pm.getLaunchIntentForPackage(app.packageName) != null)
                    }
                    .map { app ->
                        InstalledAppRow(
                            packageName = app.packageName,
                            label = runCatching { pm.getApplicationLabel(app).toString() }
                                .getOrDefault(app.packageName)
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }

    val selectedRows = remember(installed, selectedPackages) {
        installed.filter { it.packageName in selectedPackages }
    }
    val filteredApps = remember(installed, query, selectedPackages) {
        val base = if (query.isBlank()) {
            installed
        } else {
            installed.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        base.filterNot { it.packageName in selectedPackages }
    }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.FOLDER,
        tint = colors.secondary,
        title = trx("Which apps use the tunnel"),
        subtitle = trx(
            when (s.splitTunnelMode) {
                SplitTunnelMode.ALL_APPS -> "Every app uses the tunnel"
                SplitTunnelMode.ONLY_SELECTED -> "Only the apps you pick"
                SplitTunnelMode.BYPASS_SELECTED -> "The apps you pick stay direct"
            }
        ),
        onBack = onBack
    ) {
        item(key = "split-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Who uses the tunnel"))
                MarbleCard {
                    SplitTunnelMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = trx(splitModeTitle(mode)),
                            subtitle = trx(splitModeHint(mode)),
                            selected = s.splitTunnelMode == mode,
                            glyph = when (mode) {
                                SplitTunnelMode.ALL_APPS -> MarbleGlyph.GLOBE
                                SplitTunnelMode.ONLY_SELECTED -> MarbleGlyph.CHECK
                                SplitTunnelMode.BYPASS_SELECTED -> MarbleGlyph.ROUTE
                            },
                            iconTint = colors.secondary,
                            onClick = { repo.updateSettings(s.copy(splitTunnelMode = mode)) }
                        )
                    }
                }
            }
        }

        if (s.splitTunnelMode != SplitTunnelMode.ALL_APPS) {
            item(key = "split-selected") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSectionHeader(
                        title = trx("Chosen apps"),
                        badge = faDigits("${selectedPackages.size}")
                    )
                    MarbleCard {
                        if (selectedRows.isEmpty() && selectedPackages.isEmpty()) {
                            MarbleListItem(
                                title = trx("Nothing chosen yet"),
                                subtitle = if (s.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED) {
                                    trx("With nothing chosen, no app uses the tunnel")
                                } else {
                                    trx("With nothing chosen, every app uses the tunnel")
                                },
                                glyph = MarbleGlyph.INFO,
                                iconTint = colors.warning,
                                showChevron = false
                            )
                        } else {
                            selectedRows.forEachIndexed { index, app ->
                                if (index > 0) MarbleDivider()
                                MarbleListItem(
                                    title = app.label,
                                    subtitle = app.packageName,
                                    glyph = MarbleGlyph.CHECK,
                                    iconTint = colors.secondary,
                                    showChevron = false,
                                    trailing = {
                                        MarbleIconButton(
                                            glyph = MarbleGlyph.CLOSE,
                                            tint = colors.error,
                                            onClick = {
                                                repo.updateSettings(
                                                    s.copy(
                                                        splitTunnelPackages = (selectedPackages - app.packageName)
                                                            .sorted()
                                                            .joinToString(",")
                                                    )
                                                )
                                            },
                                            contentDescription = trx("Remove")
                                        )
                                    }
                                )
                            }
                            // A package that is no longer installed still counts for the VPN, so it is
                            // listed rather than silently kept in the stored string.
                            (selectedPackages - selectedRows.map { it.packageName }.toSet())
                                .sorted()
                                .forEachIndexed { index, packageName ->
                                    if (selectedRows.isNotEmpty() || index > 0) MarbleDivider()
                                    MarbleListItem(
                                        title = packageName,
                                        subtitle = trx("Not installed right now"),
                                        glyph = MarbleGlyph.INFO,
                                        iconTint = colors.onSurfaceVariant,
                                        showChevron = false,
                                        trailing = {
                                            MarbleIconButton(
                                                glyph = MarbleGlyph.CLOSE,
                                                tint = colors.error,
                                                onClick = {
                                                    repo.updateSettings(
                                                        s.copy(
                                                            splitTunnelPackages = (selectedPackages - packageName)
                                                                .sorted()
                                                                .joinToString(",")
                                                        )
                                                    )
                                                },
                                                contentDescription = trx("Remove")
                                            )
                                        }
                                    )
                                }
                        }
                    }
                }
            }

            item(key = "split-apps") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MarbleSectionHeader(title = trx("Add an app"))
                    MarbleSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = trx("Search app or package name")
                    )
                    if (filteredApps.isNotEmpty()) {
                        MarbleCard {
                            filteredApps.take(120).forEachIndexed { idx, app ->
                                if (idx > 0) MarbleDivider()
                                MarbleListItem(
                                    title = app.label,
                                    subtitle = app.packageName,
                                    glyph = MarbleGlyph.PLUS,
                                    iconTint = colors.primary,
                                    showChevron = false,
                                    onClick = {
                                        repo.updateSettings(
                                            s.copy(
                                                splitTunnelPackages = (selectedPackages + app.packageName)
                                                    .sorted()
                                                    .joinToString(",")
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    } else if (installed.isNotEmpty()) {
                        MarbleSectionFooter(
                            if (query.isBlank()) {
                                trx("Every installed app is already in the list.")
                            } else {
                                trx("No app matches that name.")
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun splitModeTitle(mode: SplitTunnelMode): String = when (mode) {
    SplitTunnelMode.ALL_APPS -> "Every app"
    SplitTunnelMode.ONLY_SELECTED -> "Only the apps I pick"
    SplitTunnelMode.BYPASS_SELECTED -> "Every app except the ones I pick"
}

private fun splitModeHint(mode: SplitTunnelMode): String = when (mode) {
    SplitTunnelMode.ALL_APPS -> "Nothing to choose; the whole phone is covered"
    SplitTunnelMode.ONLY_SELECTED -> "Apps not in the list below bypass the tunnel entirely"
    SplitTunnelMode.BYPASS_SELECTED -> "Apps in the list below use your normal connection"
}

/**
 * MARBLE_ROUTING_REBUILD_V254 — the blocking lab, cut down to what a person can act on.
 *
 * The page was the densest screen in Settings: "Live Censorship Lab", "DPI Countermeasures", a
 * prescription list printed as `→ note`, and an observation list of raw host:port pairs in
 * monospace. The scan itself is good, so it stays untouched; what changes is the reading order —
 * *what is wrong*, *what to press*, then *the raw evidence* collapsed behind one line.
 */
@Composable
fun SettingsCensorshipLabPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val scope = rememberCoroutineScope()
    val fragment = parseFragmentStrategy(s.fragmentStrategy)

    var scanning by remember { mutableStateOf(false) }
    var showEvidence by rememberSaveable { mutableStateOf(false) }
    var diagnosis by remember { mutableStateOf<CensorshipLab.Diagnosis?>(null) }
    val prescription = remember(diagnosis, s) {
        diagnosis?.let { CensorshipLab.prescribe(it, s) }
    }

    val applyPrescription: (CensorshipLab.Prescription) -> Unit = { rx ->
        var next = rx.applyTo(repo.settings)
        if (rx.requireExtendedCore) {
            next = next.copy(coreEngineId = CoreEngine.SINGBOX.id)
        }
        repo.updateSettings(next)
        repo.setRuntimeMessage("Censorship prescription applied")
    }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.SHIELD,
        tint = colors.error,
        title = trx("Iran mode & blocking test"),
        subtitle = trx("Find what blocks you, then fix it"),
        onBack = onBack
    ) {
        item(key = "lab-scan") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Check this network"))
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = trx("One scan probes the sites that usually break and says which kind of blocking you have."),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        MarbleButton(
                            label = if (scanning) Tr.now.measuring else trx("Start the scan"),
                            glyph = MarbleGlyph.RADAR,
                            enabled = !scanning,
                            onClick = {
                                val snapshot = repo.settings
                                scanning = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        CensorshipProbe.scan(snapshot)
                                    }
                                    diagnosis = result
                                    scanning = false
                                    if (snapshot.censorshipAutoApply && result.decisive) {
                                        val rx = CensorshipLab.prescribe(result, repo.settings)
                                        if (!rx.empty) applyPrescription(rx)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        diagnosis?.let { diag ->
                            val tone = when (diag.severity) {
                                CensorshipLab.Severity.CRITICAL -> colors.error
                                CensorshipLab.Severity.WARNING -> colors.warning
                                CensorshipLab.Severity.INFO -> colors.success
                            }
                            MarbleDivider()
                            Text(
                                text = trx(diag.summary),
                                color = tone,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            diag.findings.forEach { finding ->
                                Text(
                                    text = "• " + trx(finding.technique.label) + " — " + finding.evidence,
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (prescription != null && !prescription.empty) {
                                prescription.notes.forEach { note ->
                                    Text(
                                        text = "→ " + trx(note),
                                        color = colors.primary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                MarbleButton(
                                    label = trx("Apply what it suggests"),
                                    glyph = MarbleGlyph.CHECK,
                                    style = MarbleButtonStyle.TINTED,
                                    onClick = { applyPrescription(prescription) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (diag.observations.isNotEmpty()) {
                                MarbleButton(
                                    label = if (showEvidence) trx("Hide the evidence")
                                    else trx("Show the evidence"),
                                    style = MarbleButtonStyle.SECONDARY,
                                    compact = true,
                                    onClick = { showEvidence = !showEvidence }
                                )
                                if (showEvidence) {
                                    diag.observations.forEach { obs ->
                                        Text(
                                            text = (if (obs.reachable) "✓ " else "✕ ") + obs.target +
                                                " · " + trx(obs.kind.label) +
                                                if (obs.latencyMs > 0) " · ${obs.latencyMs}ms" else "",
                                            color = colors.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "iran-mode-policy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Iran mode"))
                MarbleCard {
                    listOf(
                        IranModePolicy.OFF to trx("Off"),
                        IranModePolicy.AUTO to trx("Only when the network looks filtered"),
                        IranModePolicy.ALWAYS_ON to trx("Always on")
                    ).forEachIndexed { idx, (policy, label) ->
                        if (idx > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = label,
                            subtitle = if (policy == s.iranModePolicy && repo.iranMode.summary.isNotBlank()) {
                                repo.iranMode.summary
                            } else {
                                ""
                            },
                            selected = s.iranModePolicy == policy,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.error,
                            onClick = { repo.setIranModePolicy(policy) }
                        )
                    }
                }
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Work around filtering"),
                        subtitle = trx("Smaller packets, more retries and safer DNS while Iran mode is on"),
                        checked = s.iranModeCountermeasures,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.warning,
                        onCheckedChange = { repo.updateSettings(s.copy(iranModeCountermeasures = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Iranian sites stay direct"),
                        subtitle = trx("Domestic sites keep the normal connection while this mode is on"),
                        checked = s.iranDomesticDirect,
                        glyph = MarbleGlyph.ROUTE,
                        iconTint = colors.success,
                        onCheckedChange = { repo.updateSettings(s.copy(iranDomesticDirect = it)) }
                    )
                }
            }
        }

        item(key = "tls-fragmentation") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Handshake splitting"))
                MarbleCard {
                    FragmentStrategy.entries.forEachIndexed { idx, strategy ->
                        if (idx > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = trx(strategy.label),
                            selected = fragment == strategy,
                            glyph = MarbleGlyph.LAYERS,
                            iconTint = colors.secondary,
                            onClick = { repo.updateSettings(s.copy(fragmentStrategy = strategy.id)) }
                        )
                    }
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Apply what the scan finds"),
                        subtitle = trx("Settings changed automatically after a decisive scan"),
                        checked = s.censorshipAutoApply,
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.tertiary,
                        onCheckedChange = {
                            repo.updateSettings(
                                s.copy(appBehavior = s.appBehavior.copy(censorshipAutoApply = it))
                            )
                        }
                    )
                }
            }
        }
    }
}
