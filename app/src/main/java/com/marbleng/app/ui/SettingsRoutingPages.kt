package com.marbleng.app.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.marbleng.app.core.IranAppCatalog
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
import androidx.compose.ui.platform.LocalConfiguration
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
 * MARBLE_ROUTING_CLARITY_V280 — "what goes direct", rebuilt as two choices, one rule list and one
 * proof.
 *
 * V254 answered the same complaint by rendering the decision instead of the storage, but it kept
 * four modes, six ready-made sets and a second switch for the rules. Three of those were not
 * controls: [RoutingMode.BYPASS_PRIVATE] emitted the same rules as [RoutingMode.PROXY_ALL], the
 * presets only rewrote the rule list, and the rule switch turned the list off in one core while the
 * Xray verifier still demanded a route for it. The page is now:
 *
 *  1. **Where your traffic goes** — two outcomes, each one sentence, plus the ad list, which had a
 *     switch nowhere in the app even though the engine honoured it;
 *  2. **Running right now** — the layers that really emit rules, in the order the core reads them;
 *  3. **Country lists** — the two files, one download verb, and the source it comes from;
 *  4. **How much of Iran stays direct** — the precision dial, only while it can do anything;
 *  5. **My rules** — always live, added before the country lists so an explicit rule wins;
 *  6. **Try a site** — a host in, the same steps out that the router would take;
 *  7. **Advanced** — the download source, the custom addresses, the old comma lists and the core
 *     check, all behind one row because none of them is needed to use the page.
 */
@Composable
fun SettingsRoutingPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val assets = repo.routingAssetStatus()
    val policy = RoutingOverview.policyOf(s)
    val rules = remember(s.routingRulesJson) { RoutingEngine.effectiveRules(s) }
    val layers = remember(s, assets.geoIpReady, assets.geoSiteReady) {
        RoutingOverview.layers(s, assets.geoIpReady, assets.geoSiteReady)
    }
    val duplicates = remember(rules) { RoutingOverview.duplicateMatchers(rules) }
    val regexRules = remember(rules) { RoutingOverview.regexRules(rules) }
    val legacyEntries = remember(s) { RoutingOverview.legacyListCount(s) }
    val precision = parseGeoPrecision(s.geoPrecision)
    val sources = remember(s.geoAssetSourceIds, s.geoCustomSourcesJson) {
        GeoAssetRegistry.catalog(s)
    }
    val source = remember(s.geoAssetSourceId, sources) {
        sources.firstOrNull { it.id == s.geoAssetSourceId } ?: sources.firstOrNull()
    }
    val listsReady = assets.geoIpReady && assets.geoSiteReady

    var editingRule by remember { mutableStateOf<RoutingRule?>(null) }
    var editingIndex by remember { mutableStateOf(-1) }
    var editingIsNew by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var simHost by rememberSaveable { mutableStateOf("") }
    var showAllSteps by rememberSaveable { mutableStateOf(false) }
    val simulation = remember(simHost, s, assets.geoIpReady, assets.geoSiteReady) {
        if (simHost.isBlank()) null else repo.simulateRoute(simHost.trim())
    }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.ROUTE,
        tint = colors.success,
        title = trx("What goes direct"),
        subtitle = trx(RoutingOverview.summary(policy)),
        onBack = onBack
    ) {
        // ---------------------------------------------------------------- 1. the one decision
        item(key = "routing-policy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Where your traffic goes"))
                MarbleCard(borderColor = colors.success.copy(alpha = 0.30f)) {
                    RoutingOverview.Policy.entries.forEachIndexed { index, candidate ->
                        if (index > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = trx(RoutingOverview.title(candidate)),
                            subtitle = trx(RoutingOverview.summary(candidate)),
                            selected = policy == candidate,
                            glyph = if (candidate == RoutingOverview.Policy.IRAN_DIRECT) {
                                MarbleGlyph.SHIELD
                            } else {
                                MarbleGlyph.GLOBE
                            },
                            iconTint = if (candidate == RoutingOverview.Policy.IRAN_DIRECT) {
                                colors.success
                            } else {
                                colors.primary
                            },
                            badge = if (candidate == RoutingOverview.Policy.IRAN_DIRECT) {
                                trx("Recommended")
                            } else {
                                ""
                            },
                            onClick = { repo.setRoutingPolicy(candidate) }
                        )
                    }
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Block ads and trackers"),
                        subtitle = trx("Read from the downloaded site list"),
                        checked = s.routeBlockAds,
                        glyph = MarbleGlyph.SHIELD,
                        iconTint = colors.error,
                        onCheckedChange = { repo.setAdBlocking(it) }
                    )
                }
                MarbleSectionFooter(trx(RoutingOverview.note(policy)))
            }
        }

        // ------------------------------------------------------- 2. what that decision does now
        item(key = "routing-in-force") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Running right now"),
                    badge = faDigits("${layers.size}")
                )
                MarbleCard {
                    layers.forEachIndexed { index, layer ->
                        if (index > 0) MarbleDivider()
                        val tone = layer.outbound.tone(colors)
                        MarbleListItem(
                            title = trx(layer.title),
                            subtitle = trx(layer.detail),
                            glyph = glyphOf(layer.outbound),
                            iconTint = tone,
                            showChevron = false,
                            trailing = {
                                MarbleBadge(text = trx(layer.outbound.label), color = tone)
                            }
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------- 3. the two files
        item(key = "routing-lists") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Country lists"),
                    badge = if (listsReady) trx("Ready") else trx("Needs download"),
                    badgeColor = if (listsReady) colors.success else colors.warning
                )
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
                    if (!listsReady) {
                        MarbleDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(PaddingValues(16.dp)),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = trx("The country rules wait for these files. Everything else already works."),
                                color = colors.warning,
                                style = MaterialTheme.typography.bodySmall
                            )
                            MarbleButton(
                                label = trx("Download the lists"),
                                glyph = MarbleGlyph.DOWNLOAD,
                                enabled = !repo.busy && repo.state == "DISCONNECTED",
                                onClick = { repo.prepareRoutingAssets(force = true) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                MarbleSectionFooter(
                    trx("From") + " " + trx(source?.label.orEmpty()) +
                        if (repo.state != "DISCONNECTED") {
                            " • " + trx("Disconnect first, a download cannot run inside the tunnel.")
                        } else {
                            ""
                        }
                )
            }
        }

        // ------------------------------------------------------------ 4. the domestic dial
        if (policy == RoutingOverview.Policy.IRAN_DIRECT) {
            item(key = "routing-precision") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSectionHeader(
                        title = trx("How much of Iran stays direct"),
                        badge = faDigits("${domesticEntries(precision)}") + " " + trx("extra names"),
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
                    MarbleSectionFooter(
                        trx("The downloaded lists always decide first. These levels only add names on top.")
                    )
                }
            }
        }

        // ---------------------------------------------------------------- 5. my own rules
        item(key = "routing-rules") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("My rules"),
                    badge = faDigits("${rules.count { it.enabled }}") + " / " + faDigits("${rules.size}")
                )
                MarbleCard {
                    MarbleListItem(
                        title = trx("Add a rule"),
                        subtitle = trx("Pick a site or an address, then where it goes"),
                        glyph = MarbleGlyph.PLUS,
                        iconTint = colors.primary,
                        onClick = {
                            editingIsNew = true
                            editingIndex = -1
                            editingRule = RoutingEngine.newRule()
                        }
                    )
                    rules.forEachIndexed { index, rule ->
                        MarbleDivider()
                        val tone = rule.outbound.tone(colors)
                        val issue = RoutingEngine.validateRule(rule)
                            .firstOrNull { it.severity == RoutingEngine.IssueSeverity.ERROR }
                        val paused = !rule.enabled || issue != null
                        val note = when {
                            issue != null -> trx("Needs a fix")
                            !rule.enabled -> trx("Paused")
                            duplicates[rule.id] != null -> trx("Same as an earlier rule")
                            else -> ""
                        }
                        MarbleListItem(
                            title = rule.remark.ifBlank {
                                rule.matcher.ifBlank { trx(rule.kind.label) }
                            },
                            subtitle = buildString {
                                append(trx(rule.kind.label))
                                if (rule.remark.isNotBlank()) {
                                    append(" · ")
                                    append(rule.matcher)
                                }
                                if (note.isNotBlank()) {
                                    append(" · ")
                                    append(note)
                                }
                            },
                            glyph = glyphOf(rule.outbound),
                            iconTint = if (paused) colors.onSurfaceVariant else tone,
                            titleColor = if (paused) colors.onSurfaceVariant else colors.onSurface,
                            showChevron = false,
                            onClick = {
                                editingIsNew = false
                                editingIndex = index
                                editingRule = rule
                            },
                            trailing = {
                                MarbleBadge(text = trx(rule.outbound.label), color = tone)
                            }
                        )
                    }
                }
                if (rules.isEmpty()) {
                    MarbleSectionFooter(
                        trx("No rules yet. The policy above decides everything on its own.")
                    )
                } else if (regexRules.isNotEmpty()) {
                    MarbleSectionFooter(
                        trx("Regular expressions are slow: they are checked on every request.") + " • " +
                            faDigits("${regexRules.size}")
                    )
                } else {
                    MarbleSectionFooter(
                        trx("Your rules are checked before the country lists, so a rule you write wins.")
                    )
                }
            }
        }

        // ---------------------------------------------------------------- 6. prove the route
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
                            val steps = if (showAllSteps) {
                                sim.steps
                            } else {
                                sim.steps.filter { it.matched == true }
                            }
                            steps.forEachIndexed { index, step ->
                                if (index > 0) MarbleDivider()
                                val stepTone = step.outbound?.tone(colors) ?: colors.onSurfaceVariant
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MarbleBadge(
                                        text = when {
                                            step.skipped -> trx("Skipped")
                                            step.matched == null -> trx("At runtime")
                                            step.matched == true -> trx("Matched")
                                            else -> trx("No match")
                                        },
                                        color = if (step.matched == true) {
                                            stepTone
                                        } else {
                                            colors.onSurfaceVariant
                                        }
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
                                label = if (showAllSteps) {
                                    trx("Show only what matched")
                                } else {
                                    trx("Show every step")
                                },
                                style = MarbleButtonStyle.SECONDARY,
                                compact = true,
                                onClick = { showAllSteps = !showAllSteps }
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------------- 7. everything else
        item(key = "routing-advanced") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Advanced"))
                MarbleCard {
                    MarbleListItem(
                        title = trx("Download source and custom addresses"),
                        subtitle = trx("Only needed when you replace the bundled country lists"),
                        glyph = MarbleGlyph.FOLDER,
                        iconTint = colors.tertiary,
                        onClick = { advanced = true }
                    )
                    if (legacyEntries > 0) {
                        MarbleDivider()
                        MarbleListItem(
                            title = trx("Move the old lists into my rules"),
                            subtitle = faDigits("$legacyEntries") + " " + trx("entries from an earlier version"),
                            glyph = MarbleGlyph.LAYERS,
                            iconTint = colors.warning,
                            onClick = { repo.importLegacyRoutingLists() }
                        )
                    }
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Check the policy with the core"),
                        subtitle = trx("Builds a real config and asks the core whether it runs"),
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.success,
                        enabled = !repo.busy,
                        onClick = { repo.verifyRoutingPolicy() }
                    )
                }
                MarbleSectionFooter(
                    trx("Every row above describes the config the core will read. Nothing is decided twice.")
                )
            }
        }
    }

    editingRule?.let { current ->
        RoutingRuleEditSheet(
            repo = repo,
            initial = current,
            isNew = editingIsNew,
            index = editingIndex,
            count = rules.size,
            duplicatesOf = duplicates[current.id],
            onDismiss = { editingRule = null }
        )
    }

    if (advanced) {
        RoutingAdvancedSheet(
            repo = repo,
            sources = sources,
            legacyEntries = legacyEntries,
            onDismiss = { advanced = false }
        )
    }
}

/** The one glyph vocabulary for the three outcomes, shared by every row on the page. */
private fun glyphOf(outbound: RoutingOutbound): MarbleGlyph = when (outbound) {
    RoutingOutbound.PROXY -> MarbleGlyph.TUNNEL
    RoutingOutbound.DIRECT -> MarbleGlyph.HOME
    RoutingOutbound.BLOCK -> MarbleGlyph.SHIELD
}

/** Tone per outcome; one place, so Direct, Tunnel and Blocked read as one family. */
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
    GeoPrecision.MAXIMUM -> "Also match exact domestic suffixes"
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
        "Uses the downloaded Iran lists plus exact domestic domain suffixes. Brand substrings never bypass the tunnel."
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
 * MARBLE_ROUTING_CLARITY_V280 — the rule editor, with the actions that used to live in the list.
 *
 * The list rows carried two 48dp move buttons each, which made a list of five rules read as ten
 * controls and pushed the address out of the title. Pausing, moving, copying and deleting are
 * properties of *one* rule, so they live here, next to the rule they change.
 */
@Composable
private fun RoutingRuleEditSheet(
    repo: AppRepository,
    initial: RoutingRule,
    isNew: Boolean,
    index: Int,
    count: Int,
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
            options = listOf(
                RoutingRuleKind.DOMAIN,
                RoutingRuleKind.IP,
                RoutingRuleKind.PORT,
                RoutingRuleKind.GEOSITE,
                RoutingRuleKind.GEOIP
            ).map { kind ->
                MarbleMenuOption(kind.name, trx(kind.label), trx(ruleKindHint(kind)))
            },
            onSelect = { name ->
                val picked = RoutingRuleKind.entries.firstOrNull { it.name == name }
                    ?: return@MarbleDropdownField
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
        }

        // MARBLE_ROUTING_CLARITY_V280 — "both TCP and UDP" was a switch that could only ever choose
        // between both and TCP. Three words say the whole truth and make UDP reachable.
        MarbleSegmentedControl(
            options = listOf(
                "" to trx("Both"),
                "tcp" to trx("TCP only"),
                "udp" to trx("UDP only")
            ),
            selected = draft.network.trim().lowercase(),
            onSelect = { draft = draft.copy(network = it) }
        )

        issues.forEach { issue ->
            Text(
                text = trx(issue.message),
                color = if (issue.severity == RoutingEngine.IssueSeverity.ERROR) {
                    colors.error
                } else {
                    colors.warning
                },
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

        if (!isNew) {
            MarbleSwitchRow(
                title = trx("This rule is on"),
                subtitle = trx("Off keeps it saved without using it"),
                checked = draft.enabled,
                glyph = MarbleGlyph.CHECK,
                iconTint = colors.success,
                onCheckedChange = { draft = draft.copy(enabled = it) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleButton(
                    label = trx("Move up"),
                    style = MarbleButtonStyle.SECONDARY,
                    compact = true,
                    enabled = index > 0,
                    onClick = {
                        repo.moveRoutingRule(initial.id, -1)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                MarbleButton(
                    label = trx("Move down"),
                    style = MarbleButtonStyle.SECONDARY,
                    compact = true,
                    enabled = index >= 0 && index < count - 1,
                    onClick = {
                        repo.moveRoutingRule(initial.id, 1)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                MarbleButton(
                    label = trx("Copy"),
                    style = MarbleButtonStyle.SECONDARY,
                    compact = true,
                    onClick = {
                        repo.duplicateRoutingRule(initial.id)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
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
                            RoutingEngine.effectiveRules(repo.settings)
                                .filterNot { it.id == initial.id }
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
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
 * MARBLE_ROUTING_CLARITY_V280 — the drawer for the four things the page does not need to show.
 *
 * The download source, the two custom URLs (shown only for the custom source, because that is the
 * only time they are read), the old comma lists and the core check. The five text boxes V254 put
 * here are gone: each one duplicated a rule, and the two could disagree.
 */
@Composable
private fun RoutingAdvancedSheet(
    repo: AppRepository,
    sources: List<GeoAssetRegistry.SourceSpec>,
    legacyEntries: Int,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val selected = sources.firstOrNull { it.id == s.geoAssetSourceId } ?: sources.firstOrNull()
    val custom = selected?.custom == true

    MarbleBottomSheet(
        title = trx("Advanced"),
        subtitle = trx("Optional. The policy above works without any of it."),
        onDismiss = onDismiss,
        scrollable = true
    ) {
        MarbleDropdownField(
            label = trx("Download source"),
            value = selected?.id.orEmpty(),
            options = sources.map { spec ->
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
            onSelect = { id -> repo.applyGeoAssetSource(id) }
        )

        if (custom) {
            MarbleTextField(
                label = trx("Address list download"),
                value = s.geoIpUrl,
                onValueChange = { url -> repo.updateSettings(s.copy(geoIpUrl = url.trim())) },
                placeholder = "https://…/geoip.dat"
            )
            MarbleTextField(
                label = trx("Site list download"),
                value = s.geoSiteUrl,
                onValueChange = { url -> repo.updateSettings(s.copy(geoSiteUrl = url.trim())) },
                placeholder = "https://…/geosite.dat"
            )
        }

        MarbleSectionFooter(
            trx("Only https:// downloads are accepted. A download cannot run while the tunnel is connected.")
        )

        if (legacyEntries > 0) {
            Text(
                text = trx("The lists from the earlier layout still hold") + " " +
                    faDigits("$legacyEntries") + " " + trx("entries. They work, but they are a second place to look."),
                color = colors.warning,
                style = MaterialTheme.typography.bodySmall
            )
            MarbleButton(
                label = trx("Move them into my rules"),
                glyph = MarbleGlyph.LAYERS,
                style = MarbleButtonStyle.TINTED,
                onClick = {
                    repo.importLegacyRoutingLists()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

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
    val persian = LocalMarbleStrings.current.language == MarbleLanguage.FA
    var query by rememberSaveable { mutableStateOf("") }
    var installed by remember { mutableStateOf<List<InstalledAppRow>>(emptyList()) }
    var reviewDomestic by remember { mutableStateOf(false) }
    var appsLoaded by remember { mutableStateOf(false) }
    var approvedDomestic by remember { mutableStateOf<Set<String>>(emptySet()) }
    val domesticApps = remember(installed) {
        val matches = IranAppCatalog.suggestions(installed.map { it.packageName })
        installed.filter { it.packageName in matches }
    }
    if (reviewDomestic) {
        AlertDialog(
            onDismissRequest = { reviewDomestic = false },
            title = { Text(if (persian) "بررسی برنامه‌های پیشنهادی" else "Review suggested apps") },
            text = {
                Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.55f).dp).verticalScroll(rememberScrollState())) {
                    Text(if (persian)
                        "این بررسی فقط روی گوشی انجام می‌شود و همهٔ برنامه‌های ایرانی را تضمین نمی‌کند. با تأیید، برنامه‌های زیر بدون VPN کار می‌کنند. فهرست قبلی در حالت «فقط انتخاب‌شده‌ها» جایگزین می‌شود. برای اعمال، اتصال را دوباره برقرار کنید."
                    else "Checked locally; this is not a complete nationality detector. Confirming lets these apps bypass the VPN. An existing only-selected list will be replaced. Reconnect to apply.")
                    domesticApps.forEach { app ->
                        MarbleSwitchRow(
                            title = app.label, subtitle = app.packageName,
                            checked = app.packageName in approvedDomestic,
                            onCheckedChange = { checked ->
                                approvedDomestic = if (checked) approvedDomestic + app.packageName
                                    else approvedDomestic - app.packageName
                            }
                        )
                    }
                    if (domesticApps.isEmpty()) Text(if (persian)
                        "برنامهٔ شناخته‌شده‌ای پیدا نشد؛ می‌توانید دستی انتخاب کنید."
                    else "No known matches; you can select apps manually.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val latest = repo.settings
                    val existing = if (latest.splitTunnelMode == SplitTunnelMode.BYPASS_SELECTED)
                        latest.splitTunnelPackages.split(',', '\n', ' ').filter { it.isNotBlank() }.toSet()
                    else emptySet()
                    repo.updateSettings(latest.copy(
                        splitTunnelMode = SplitTunnelMode.BYPASS_SELECTED,
                        splitTunnelPackages = (existing + approvedDomestic).sorted().joinToString(",")
                    ))
                    reviewDomestic = false
                }, enabled = approvedDomestic.isNotEmpty()) {
                    Text(if (persian) "تأیید خروج از VPN" else "Confirm bypass")
                }
            },
            dismissButton = { TextButton(onClick = { reviewDomestic = false }) {
                Text(if (persian) "انصراف" else "Cancel")
            } }
        )
    }

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
        appsLoaded = true
    }

    LaunchedEffect(installed) {
        approvedDomestic = domesticApps.map { it.packageName }.toSet()
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
        item(key = "domestic-app-review") {
            MarbleCard {
                MarbleListItem(
                    title = if (persian) "بررسی محلی برنامه‌های ایرانی" else "Review domestic apps",
                    subtitle = if (!appsLoaded) {
                        if (persian) "در حال خواندن برنامه‌های قابل مشاهده…" else "Reading visible apps…"
                    } else if (persian) "${faDigits(domesticApps.size.toString())} پیشنهاد • بدون ارسال اطلاعات • تأیید پیش از اعمال"
                    else "${domesticApps.size} local suggestions • Review before bypass",
                    glyph = MarbleGlyph.ROUTE,
                    iconTint = colors.secondary,
                    enabled = appsLoaded,
                    onClick = {
                        approvedDomestic = domesticApps.map { it.packageName }.toSet()
                        reviewDomestic = true
                    }
                )
            }
        }
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
                    // MARBLE_ROUTING_CLARITY_V280 — the domestic switch that used to sit here was a
                    // second control for `iranDomesticDirect`, the flag the routing policy already
                    // owns. Two pages for one decision is how the old routing screen ended up
                    // disagreeing with the core; "What goes direct" is the one place that writes it.
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
