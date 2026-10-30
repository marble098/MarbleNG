package com.marbleng.app.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.AddressFamilyPolicy
import com.marbleng.app.core.CensorshipLab
import com.marbleng.app.core.CensorshipProbe
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.GeoAssetRegistry
import com.marbleng.app.core.RoutingEngine
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
    val geoCatalog = remember(s.geoAssetSourceIds, s.geoCustomSourcesJson, s.geoMultiSourceEnabled) {
        GeoAssetRegistry.catalog(s)
    }

    var editingRule by remember { mutableStateOf<RoutingRule?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var simHost by rememberSaveable { mutableStateOf("") }
    val simulation = remember(simHost, s, repo.geoGateNote) {
        if (simHost.isBlank()) null else repo.simulateRoute(simHost.trim())
    }

    MarbleSubPageScaffold(
        title = trx("What goes direct"),
        onBack = onBack
    ) {
        item(key = "routing-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Routing Mode"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Enable Custom Routing"),
                        subtitle = trx("Match domain, IP and country rules"),
                        checked = s.customRoutingEnabled,
                        glyph = MarbleGlyph.ROUTE,
                        iconTint = colors.success,
                        onCheckedChange = { repo.updateSettings(s.copy(customRoutingEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleChoiceRow(
                        title = trx("Proxy All"),
                        subtitle = trx("Send everything through the server"),
                        selected = s.routingMode == RoutingMode.PROXY_ALL,
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = colors.primary,
                        onClick = { repo.updateSettings(s.copy(routingMode = RoutingMode.PROXY_ALL)) }
                    )
                    MarbleDivider()
                    MarbleChoiceRow(
                        title = trx("Bypass Private LAN"),
                        subtitle = trx("Keep home network devices direct"),
                        selected = s.routingMode == RoutingMode.BYPASS_PRIVATE,
                        glyph = MarbleGlyph.HOME,
                        iconTint = colors.tertiary,
                        onClick = { repo.updateSettings(s.copy(routingMode = RoutingMode.BYPASS_PRIVATE)) }
                    )
                    MarbleDivider()
                    MarbleChoiceRow(
                        title = trx("Geo Direct"),
                        subtitle = trx("Keep Iranian sites direct"),
                        selected = s.routingMode == RoutingMode.GEO_DIRECT,
                        glyph = MarbleGlyph.SHIELD,
                        iconTint = colors.success,
                        onClick = { repo.updateSettings(s.copy(routingMode = RoutingMode.GEO_DIRECT)) }
                    )
                    MarbleDivider()
                    MarbleChoiceRow(
                        title = trx("Custom Rules Only"),
                        subtitle = trx("Evaluate only the custom rule list below"),
                        selected = s.routingMode == RoutingMode.CUSTOM,
                        glyph = MarbleGlyph.TUNERS,
                        iconTint = colors.secondary,
                        onClick = { repo.updateSettings(s.copy(routingMode = RoutingMode.CUSTOM)) }
                    )
                }
            }
        }

        item(key = "routing-presets-domain") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Rule Presets & Domain Strategy"))
                MarbleChipStrip {
                    RoutingPresets.Preset.entries.forEach { preset ->
                        MarblePillChip(
                            label = trx(preset.title),
                            selected = rules.map { it.id } == preset.rules.map { it.id },
                            accent = colors.success,
                            onClick = { repo.applyRoutingPreset(preset) }
                        )
                    }
                }
                MarbleChipStrip {
                    listOf(
                        "IPIfNonMatch" to trx("Domain First"),
                        "IPOnDemand" to trx("IP On Demand"),
                        "AsIs" to trx("As-Is")
                    ).forEach { (strategy, label) ->
                        MarblePillChip(
                            label = label,
                            selected = s.routeDomainStrategy == strategy,
                            accent = colors.primary,
                            onClick = { repo.updateSettings(s.copy(routeDomainStrategy = strategy)) }
                        )
                    }
                }
            }
        }

        item(key = "routing-geo-assets") {
            val precision = parseGeoPrecision(s.geoPrecision)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("GeoIP & GeoSite Databases"))
                MarbleCard {
                    MarbleListItem(
                        title = "GeoIP · ${if (assets.geoIpReady) formatBytes(assets.geoIpBytes) else trx("Missing")}",
                        subtitle = if (assets.geoIpReady) trx(relativeTimeLabel(assets.geoIpUpdatedAt)) else trx("Download required"),
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = if (assets.geoIpReady) colors.success else colors.warning,
                        showChevron = false
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = "GeoSite · ${if (assets.geoSiteReady) formatBytes(assets.geoSiteBytes) else trx("Missing")}",
                        subtitle = if (assets.geoSiteReady) trx(relativeTimeLabel(assets.geoSiteUpdatedAt)) else trx("Download required"),
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = if (assets.geoSiteReady) colors.success else colors.warning,
                        showChevron = false
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Multi-Source Geo Databases"),
                        subtitle = trx("Combine rules from several sources"),
                        checked = s.geoMultiSourceEnabled,
                        glyph = MarbleGlyph.FOLDER,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(geoMultiSourceEnabled = it)) }
                    )
                    geoCatalog.forEach { spec ->
                        MarbleDivider()
                        val selected = spec.id in geoIds
                        MarbleChoiceRow(
                            title = spec.label,
                            subtitle = spec.id,
                            selected = selected,
                            glyph = MarbleGlyph.DOWNLOAD,
                            iconTint = colors.tertiary,
                            onClick = {
                                if (s.geoMultiSourceEnabled && selected && spec.id != geoIds.firstOrNull()) {
                                    repo.toggleGeoAssetSource(spec.id)
                                } else {
                                    repo.applyGeoAssetSource(spec.id)
                                }
                            }
                        )
                    }
                }

                MarbleChipStrip {
                    GeoPrecision.entries.forEach { level ->
                        MarblePillChip(
                            label = trx(level.name),
                            selected = precision == level,
                            accent = colors.success,
                            onClick = { repo.setGeoPrecision(level) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MarbleButton(
                        label = trx("Update Geo Files"),
                        glyph = MarbleGlyph.DOWNLOAD,
                        enabled = !repo.busy,
                        onClick = { repo.prepareRoutingAssets(force = true) },
                        modifier = Modifier.weight(1f)
                    )
                    MarbleButton(
                        label = trx("Verify Policy"),
                        glyph = MarbleGlyph.CHECK,
                        style = MarbleButtonStyle.TINTED,
                        enabled = !repo.busy,
                        onClick = { repo.verifyRoutingPolicy() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "routing-rules-list") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = "${trx("Routing Rules")} (${rules.size})")
                MarbleCard {
                    MarbleListItem(
                        title = trx("Add Routing Rule"),
                        subtitle = trx("Make a rule for a site, IP or port"),
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
                    rules.forEach { rule ->
                        MarbleDivider()
                        val outTone = when (rule.outbound) {
                            RoutingOutbound.PROXY -> colors.primary
                            RoutingOutbound.DIRECT -> colors.success
                            RoutingOutbound.BLOCK -> colors.error
                        }
                        MarbleListItem(
                            title = rule.remark.ifBlank { trx(rule.kind.label) + ": " + rule.matcher },
                            subtitle = trx(rule.kind.label) + " · " + rule.matcher,
                            glyph = MarbleGlyph.ROUTE,
                            iconTint = outTone,
                            trailing = {
                                MarbleBadge(text = trx(rule.outbound.label), color = outTone)
                            },
                            onClick = {
                                editingIsNew = false
                                editingRule = rule
                            }
                        )
                    }
                }
            }
        }

        item(key = "routing-simulator") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Route Simulator"))
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MarbleTextField(
                            label = trx("Test Hostname or IP"),
                            value = simHost,
                            onValueChange = { simHost = it },
                            placeholder = trx("example.com or 1.1.1.1")
                        )
                        simulation?.let { sim ->
                            val verdictColor = when (sim.verdict) {
                                RoutingOutbound.PROXY -> colors.primary
                                RoutingOutbound.DIRECT -> colors.success
                                RoutingOutbound.BLOCK -> colors.error
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MarbleBadge(text = sim.verdict.name, color = verdictColor)
                                Text(
                                    text = trx(sim.verdictReason),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingRule?.let { current ->
        RoutingRuleEditSheet(
            repo = repo,
            initial = current,
            isNew = editingIsNew,
            onDismiss = { editingRule = null }
        )
    }
}

@Composable
private fun RoutingRuleEditSheet(
    repo: AppRepository,
    initial: RoutingRule,
    isNew: Boolean,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val issues = remember(draft) { RoutingEngine.validateRule(draft) }
    val hasErrors = issues.any { it.severity == RoutingEngine.IssueSeverity.ERROR }

    MarbleBottomSheet(
        title = if (isNew) trx("New Routing Rule") else trx("Edit Routing Rule"),
        onDismiss = onDismiss
    ) {
        MarbleTextField(
            label = trx("Rule Label (optional)"),
            value = draft.remark,
            onValueChange = { draft = draft.copy(remark = it) }
        )
        MarbleDropdownField(
            label = trx("Match Kind"),
            value = trx(draft.kind.label),
            options = RoutingRuleKind.entries.map { MarbleMenuOption(it.name, trx(it.label)) },
            onSelect = { name ->
                val picked = RoutingRuleKind.entries.firstOrNull { it.name == name } ?: return@MarbleDropdownField
                draft = draft.copy(kind = picked)
            }
        )
        MarbleTextField(
            label = trx("Matcher Value"),
            value = draft.matcher,
            onValueChange = { draft = draft.copy(matcher = it) },
            placeholder = "ir, category-ads-all, example.com, 443"
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
        issues.forEach { issue ->
            Text(
                text = trx(issue.message),
                color = if (issue.severity == RoutingEngine.IssueSeverity.ERROR) colors.error else colors.warning,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MarbleButton(
                label = if (isNew) trx("Add Rule") else trx("Save Rule"),
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
                            RoutingEngine.effectiveRules(repo.settings).filterNot { it.id == initial.id }
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
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

    MarbleSubPageScaffold(
        title = trx("DNS and IP version"),
        onBack = onBack
    ) {
        item(key = "family-policy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("IPv4 / IPv6 Address Family"))
                MarbleCard {
                    MarbleListItem(
                        title = AddressFamilyPolicy.describe(plan),
                        subtitle = "${underlay.label} · " + trx("DNS") + " " + trx(PlainVoice.dnsStrategyLabel(plan.dnsQueryStrategy)),
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = colors.tertiary,
                        showChevron = false
                    )
                    listOf(
                        AddressFamilyMode.SMART to trx("Smart Dual-Stack"),
                        AddressFamilyMode.PREFER_IPV4 to trx("Prefer IPv4"),
                        AddressFamilyMode.PREFER_IPV6 to trx("Prefer IPv6"),
                        AddressFamilyMode.FORCE_IPV4 to trx("Force IPv4"),
                        AddressFamilyMode.FORCE_IPV6 to trx("Force IPv6")
                    ).forEach { (mode, label) ->
                        MarbleDivider()
                        MarbleChoiceRow(
                            title = label,
                            selected = s.addressFamilyMode == mode,
                            glyph = MarbleGlyph.ROUTE,
                            iconTint = colors.primary,
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
                            title = trx("Strict Family Enforcement"),
                            subtitle = trx("Refuse servers that cannot use the forced IP version"),
                            checked = s.strictAddressFamily,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.warning,
                            onCheckedChange = { repo.updateSettings(s.copy(strictAddressFamily = it)) }
                        )
                    }
                }
            }
        }

        item(key = "dns-switches") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Encrypted DNS Protection"))
                MarbleCard {
                    if (s.connectionMode != ConnectionMode.FULL_TUN || !s.dnsHijackEnabled) {
                        MarbleSwitchRow(
                            title = trx("Intercept Port 53 DNS"),
                            subtitle = trx("Capture plaintext DNS queries inside the tunnel"),
                            checked = s.dnsHijackEnabled,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.success,
                            onCheckedChange = { repo.updateSettings(s.copy(dnsHijackEnabled = it)) }
                        )
                        MarbleDivider()
                    }
                    MarbleSwitchRow(
                        title = trx("Fake IP DNS"),
                        subtitle = trx("Return instant local synthetic IPs and resolve in-tunnel"),
                        checked = s.dnsFakeIpEnabled,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(dnsFakeIpEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Adaptive DoH Ordering"),
                        subtitle = trx("Prefer the fastest healthy encrypted DNS endpoint"),
                        checked = s.adaptiveDnsEnabled,
                        glyph = MarbleGlyph.GAUGE,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(adaptiveDnsEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Adaptive Dual-Stack DNS"),
                        subtitle = trx("Match DNS record family to active link capabilities"),
                        checked = s.adaptiveDualStackEnabled,
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(adaptiveDualStackEnabled = it)) }
                    )
                }
            }
        }

        item(key = "dns-resolvers") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Resolvers"))
                MarbleChipStrip {
                    listOf(
                        Triple("Cloudflare", "1.1.1.1" to "1.0.0.1", "https://1.1.1.1/dns-query" to "https://1.0.0.1/dns-query"),
                        Triple("Google", "8.8.8.8" to "8.8.4.4", "https://8.8.8.8/dns-query" to "https://8.8.4.4/dns-query"),
                        Triple("Quad9", "9.9.9.9" to "149.112.112.112", "https://9.9.9.9/dns-query" to "https://149.112.112.112/dns-query")
                    ).forEach { (name, ips, dohs) ->
                        MarblePillChip(
                            label = name,
                            selected = s.dnsPrimaryIp == ips.first,
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

                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MarbleTextField(
                                label = "TUN DNS 1",
                                value = s.dnsPrimaryIp,
                                onValueChange = { repo.updateSettings(s.copy(dnsPrimaryIp = it.trim())) },
                                modifier = Modifier.weight(1f)
                            )
                            MarbleTextField(
                                label = "TUN DNS 2",
                                value = s.dnsSecondaryIp,
                                onValueChange = { repo.updateSettings(s.copy(dnsSecondaryIp = it.trim())) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        MarbleTextField(
                            label = trx("Primary DoH URL"),
                            value = s.dnsPrimaryDoH,
                            onValueChange = { repo.updateSettings(s.copy(dnsPrimaryDoH = it.trim())) }
                        )
                        MarbleTextField(
                            label = trx("Secondary DoH URL"),
                            value = s.dnsSecondaryDoH,
                            onValueChange = { repo.updateSettings(s.copy(dnsSecondaryDoH = it.trim())) }
                        )
                    }
                }
            }
        }
    }
}

private data class InstalledAppRow(
    val packageName: String,
    val label: String
)

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

    val filteredApps = remember(installed, query, selectedPackages) {
        val base = if (query.isBlank()) {
            installed
        } else {
            installed.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        base.sortedByDescending { it.packageName in selectedPackages }
    }

    MarbleSubPageScaffold(
        title = trx("Which apps use the tunnel"),
        onBack = onBack
    ) {
        item(key = "split-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("App Tunnel Mode"))
                MarbleCard {
                    MarbleChoiceRow(
                        title = trx("All Apps"),
                        subtitle = trx("Every app on the device uses the VPN"),
                        selected = s.splitTunnelMode == SplitTunnelMode.ALL_APPS,
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = colors.primary,
                        onClick = { repo.updateSettings(s.copy(splitTunnelMode = SplitTunnelMode.ALL_APPS)) }
                    )
                    MarbleDivider()
                    MarbleChoiceRow(
                        title = trx("Only Selected Apps"),
                        subtitle = trx("Only checked apps below enter the VPN"),
                        selected = s.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED,
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.success,
                        onClick = { repo.updateSettings(s.copy(splitTunnelMode = SplitTunnelMode.ONLY_SELECTED)) }
                    )
                    MarbleDivider()
                    MarbleChoiceRow(
                        title = trx("Bypass Selected Apps"),
                        subtitle = trx("Checked apps go direct; all other apps use the VPN"),
                        selected = s.splitTunnelMode == SplitTunnelMode.BYPASS_SELECTED,
                        glyph = MarbleGlyph.ROUTE,
                        iconTint = colors.secondary,
                        onClick = { repo.updateSettings(s.copy(splitTunnelMode = SplitTunnelMode.BYPASS_SELECTED)) }
                    )
                }
            }
        }

        if (s.splitTunnelMode != SplitTunnelMode.ALL_APPS) {
            item(key = "split-apps") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MarbleSectionHeader(title = "${trx("Installed Apps")} (${selectedPackages.size} ${trx("selected")})")
                    MarbleSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = trx("Search app or package name")
                    )
                    if (filteredApps.isNotEmpty()) {
                        MarbleCard {
                            filteredApps.take(80).forEachIndexed { idx, app ->
                                if (idx > 0) MarbleDivider()
                                val checked = app.packageName in selectedPackages
                                MarbleSwitchRow(
                                    title = app.label,
                                    subtitle = app.packageName,
                                    checked = checked,
                                    glyph = MarbleGlyph.FOLDER,
                                    iconTint = if (checked) colors.secondary else colors.onSurfaceVariant,
                                    onCheckedChange = { on ->
                                        val next = if (on) {
                                            selectedPackages + app.packageName
                                        } else {
                                            selectedPackages - app.packageName
                                        }
                                        repo.updateSettings(
                                            s.copy(splitTunnelPackages = next.sorted().joinToString(","))
                                        )
                                    }
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
fun SettingsCensorshipLabPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val scope = rememberCoroutineScope()
    val fragment = parseFragmentStrategy(s.fragmentStrategy)

    var scanning by remember { mutableStateOf(false) }
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

    MarbleSubPageScaffold(
        title = trx("Iran mode & blocking test"),
        onBack = onBack
    ) {
        item(key = "iran-mode-policy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Iran Mode"))
                MarbleCard {
                    listOf(
                        IranModePolicy.OFF to trx("Off"),
                        IranModePolicy.AUTO to trx("Automatic ISP Detection"),
                        IranModePolicy.ALWAYS_ON to trx("Always Active")
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
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("DPI Countermeasures"),
                        subtitle = trx("Fight filtering with timeouts, safer DNS and MTU repair"),
                        checked = s.iranModeCountermeasures,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.warning,
                        onCheckedChange = { repo.updateSettings(s.copy(iranModeCountermeasures = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Domestic Services Direct"),
                        subtitle = trx("Keep domestic sites direct when Iran Mode is active"),
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
                MarbleSectionHeader(title = trx("TLS Handshake Fragmentation"))
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
                        title = trx("Apply clear results automatically"),
                        subtitle = trx("Apply recommended bypass settings immediately after a scan"),
                        checked = s.censorshipAutoApply,
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(appBehavior = s.appBehavior.copy(censorshipAutoApply = it))) }
                    )
                }
            }
        }

        item(key = "censorship-lab-scanner") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Live Censorship Lab"))
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = trx("Checks your network for blocking and shows what it finds."),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        MarbleButton(
                            label = if (scanning) Tr.now.measuring else trx("Run Censorship Scan"),
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
                            val verdictColor = when (diag.severity) {
                                CensorshipLab.Severity.CRITICAL -> colors.error
                                CensorshipLab.Severity.WARNING -> colors.warning
                                CensorshipLab.Severity.INFO -> colors.success
                            }
                            Text(
                                text = trx(diag.summary),
                                color = verdictColor,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            diag.findings.forEach { finding ->
                                Text(
                                    text = "• ${finding.technique.label}: ${finding.evidence}",
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            diag.observations.forEach { obs ->
                                Text(
                                    text = "${if (obs.reachable) "✓" else "✕"} ${obs.target} " + trx(obs.kind.label) + " " + (if (obs.latencyMs > 0) "${obs.latencyMs}ms" else ""),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                            if (prescription != null && !prescription.empty) {
                                prescription.notes.forEach { note ->
                                    Text(
                                        text = "→ ${trx(note)}",
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
                        }
                    }
                }
            }
        }
    }
}
