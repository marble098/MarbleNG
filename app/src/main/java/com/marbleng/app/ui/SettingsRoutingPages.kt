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

@Composable
fun SettingsRoutingPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
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

    IosSubPageScaffold(
        title = trx("Routing & Geo Databases"),
        onBack = onBack
    ) {
        item(key = "routing-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Routing Mode"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Enable Custom Routing"),
                        subtitle = trx("Apply domain, IP and Geo rules to tunnel traffic"),
                        checked = s.customRoutingEnabled,
                        glyph = MarbleGlyph.ROUTE,
                        squircleColor = colors.green,
                        onCheckedChange = { repo.updateSettings(s.copy(customRoutingEnabled = it)) }
                    )
                    IosDivider()
                    IosChoiceRow(
                        title = trx("Proxy All"),
                        subtitle = trx("Send every connection through the proxy server"),
                        selected = s.routingMode == RoutingMode.PROXY_ALL,
                        glyph = MarbleGlyph.GLOBE,
                        squircleColor = colors.blue,
                        onClick = { repo.updateSettings(s.copy(routingMode = RoutingMode.PROXY_ALL)) }
                    )
                    IosDivider()
                    IosChoiceRow(
                        title = trx("Bypass Private LAN"),
                        subtitle = trx("Keep local Wi-Fi and RFC1918 addresses direct"),
                        selected = s.routingMode == RoutingMode.BYPASS_PRIVATE,
                        glyph = MarbleGlyph.HOME,
                        squircleColor = colors.teal,
                        onClick = { repo.updateSettings(s.copy(routingMode = RoutingMode.BYPASS_PRIVATE)) }
                    )
                    IosDivider()
                    IosChoiceRow(
                        title = trx("Geo Direct"),
                        subtitle = trx("Send configured domestic country traffic directly"),
                        selected = s.routingMode == RoutingMode.GEO_DIRECT,
                        glyph = MarbleGlyph.SHIELD,
                        squircleColor = colors.green,
                        onClick = { repo.updateSettings(s.copy(routingMode = RoutingMode.GEO_DIRECT)) }
                    )
                    IosDivider()
                    IosChoiceRow(
                        title = trx("Custom Rules Only"),
                        subtitle = trx("Evaluate only the custom rule list below"),
                        selected = s.routingMode == RoutingMode.CUSTOM,
                        glyph = MarbleGlyph.TUNERS,
                        squircleColor = colors.indigo,
                        onClick = { repo.updateSettings(s.copy(routingMode = RoutingMode.CUSTOM)) }
                    )
                }
            }
        }

        item(key = "routing-presets-domain") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("Rule Presets & Domain Strategy"))
                IosChipStrip {
                    RoutingPresets.Preset.entries.forEach { preset ->
                        IosPillChip(
                            label = trx(preset.title),
                            selected = rules.map { it.id } == preset.rules.map { it.id },
                            accent = colors.green,
                            onClick = { repo.applyRoutingPreset(preset) }
                        )
                    }
                }
                IosChipStrip {
                    listOf(
                        "IPIfNonMatch" to trx("Domain First"),
                        "IPOnDemand" to trx("IP On Demand"),
                        "AsIs" to trx("As-Is")
                    ).forEach { (strategy, label) ->
                        IosPillChip(
                            label = label,
                            selected = s.routeDomainStrategy == strategy,
                            accent = colors.blue,
                            onClick = { repo.updateSettings(s.copy(routeDomainStrategy = strategy)) }
                        )
                    }
                }
            }
        }

        item(key = "routing-geo-assets") {
            val precision = parseGeoPrecision(s.geoPrecision)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("GeoIP & GeoSite Databases"))
                IosCard {
                    IosListItem(
                        title = "GeoIP · ${if (assets.geoIpReady) formatBytes(assets.geoIpBytes) else trx("Missing")}",
                        subtitle = if (assets.geoIpReady) relativeTimeLabel(assets.geoIpUpdatedAt) else trx("Download required"),
                        glyph = MarbleGlyph.GLOBE,
                        squircleColor = if (assets.geoIpReady) colors.green else colors.orange,
                        showChevron = false
                    )
                    IosDivider()
                    IosListItem(
                        title = "GeoSite · ${if (assets.geoSiteReady) formatBytes(assets.geoSiteBytes) else trx("Missing")}",
                        subtitle = if (assets.geoSiteReady) relativeTimeLabel(assets.geoSiteUpdatedAt) else trx("Download required"),
                        glyph = MarbleGlyph.LAYERS,
                        squircleColor = if (assets.geoSiteReady) colors.green else colors.orange,
                        showChevron = false
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Multi-Source Geo Databases"),
                        subtitle = trx("Combine tags across multiple geo rule repositories"),
                        checked = s.geoMultiSourceEnabled,
                        glyph = MarbleGlyph.FOLDER,
                        squircleColor = colors.indigo,
                        onCheckedChange = { repo.updateSettings(s.copy(geoMultiSourceEnabled = it)) }
                    )
                    geoCatalog.forEach { spec ->
                        IosDivider()
                        val selected = spec.id in geoIds
                        IosChoiceRow(
                            title = spec.label,
                            subtitle = spec.id,
                            selected = selected,
                            glyph = MarbleGlyph.DOWNLOAD,
                            squircleColor = colors.teal,
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

                IosChipStrip {
                    GeoPrecision.entries.forEach { level ->
                        IosPillChip(
                            label = trx(level.name),
                            selected = precision == level,
                            accent = colors.green,
                            onClick = { repo.setGeoPrecision(level) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IosButton(
                        label = trx("Update Geo Files"),
                        glyph = MarbleGlyph.DOWNLOAD,
                        enabled = !repo.busy,
                        onClick = { repo.prepareRoutingAssets(force = true) },
                        modifier = Modifier.weight(1f)
                    )
                    IosButton(
                        label = trx("Verify Policy"),
                        glyph = MarbleGlyph.CHECK,
                        style = IosButtonStyle.TINTED,
                        enabled = !repo.busy,
                        onClick = { repo.verifyRoutingPolicy() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "routing-rules-list") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = "${trx("Routing Rules")} (${rules.size})")
                IosCard {
                    IosListItem(
                        title = trx("Add Routing Rule"),
                        subtitle = trx("Create a GeoSite, GeoIP, Domain, IP or Port rule"),
                        glyph = MarbleGlyph.PLUS,
                        squircleColor = colors.accent,
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
                        IosDivider()
                        val outTone = when (rule.outbound) {
                            RoutingOutbound.PROXY -> colors.blue
                            RoutingOutbound.DIRECT -> colors.green
                            RoutingOutbound.BLOCK -> colors.red
                        }
                        IosListItem(
                            title = rule.remark.ifBlank { "${rule.kind.name}: ${rule.matcher}" },
                            subtitle = "${rule.kind.name} · ${rule.matcher}",
                            glyph = MarbleGlyph.ROUTE,
                            squircleColor = outTone,
                            trailing = {
                                IosBadge(text = rule.outbound.name, color = outTone)
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
                IosSectionHeader(title = trx("Route Simulator"))
                IosCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IosTextField(
                            label = trx("Test Hostname or IP"),
                            value = simHost,
                            onValueChange = { simHost = it },
                            placeholder = "example.com or 1.1.1.1"
                        )
                        simulation?.let { sim ->
                            val verdictColor = when (sim.verdict) {
                                RoutingOutbound.PROXY -> colors.blue
                                RoutingOutbound.DIRECT -> colors.green
                                RoutingOutbound.BLOCK -> colors.red
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IosBadge(text = sim.verdict.name, color = verdictColor)
                                Text(
                                    text = trx(sim.verdictReason),
                                    color = colors.title,
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
    val colors = IosTheme.colors
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val issues = remember(draft) { RoutingEngine.validateRule(draft) }
    val hasErrors = issues.any { it.severity == RoutingEngine.IssueSeverity.ERROR }

    IosBottomSheet(
        title = if (isNew) trx("New Routing Rule") else trx("Edit Routing Rule"),
        onDismiss = onDismiss
    ) {
        IosTextField(
            label = trx("Rule Label (optional)"),
            value = draft.remark,
            onValueChange = { draft = draft.copy(remark = it) }
        )
        IosDropdownField(
            label = trx("Match Kind"),
            value = draft.kind.name,
            options = RoutingRuleKind.entries.map { IosMenuOption(it.name) },
            onSelect = { name ->
                val picked = RoutingRuleKind.entries.firstOrNull { it.name == name } ?: return@IosDropdownField
                draft = draft.copy(kind = picked)
            }
        )
        IosTextField(
            label = trx("Matcher Value"),
            value = draft.matcher,
            onValueChange = { draft = draft.copy(matcher = it) },
            placeholder = "ir, category-ads-all, example.com, 443"
        )
        IosSegmentedControl(
            options = listOf(
                RoutingOutbound.PROXY to "PROXY",
                RoutingOutbound.DIRECT to "DIRECT",
                RoutingOutbound.BLOCK to "BLOCK"
            ),
            selected = draft.outbound,
            onSelect = { draft = draft.copy(outbound = it) }
        )
        issues.forEach { issue ->
            Text(
                text = trx(issue.message),
                color = if (issue.severity == RoutingEngine.IssueSeverity.ERROR) colors.red else colors.orange,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IosButton(
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
                IosButton(
                    label = trx("Delete"),
                    style = IosButtonStyle.DESTRUCTIVE,
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
    val colors = IosTheme.colors
    val s = repo.settings
    val underlay = repo.networkSnapshot
    val plan = AddressFamilyPolicy.plan(settings = s, underlayHasIpv6 = underlay.hasIpv6)

    IosSubPageScaffold(
        title = trx("DNS & IPv4 / IPv6"),
        onBack = onBack
    ) {
        item(key = "family-policy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("IPv4 / IPv6 Address Family"))
                IosCard {
                    IosListItem(
                        title = AddressFamilyPolicy.describe(plan),
                        subtitle = "${underlay.label} · DNS ${plan.dnsQueryStrategy}",
                        glyph = MarbleGlyph.GLOBE,
                        squircleColor = colors.cyan,
                        showChevron = false
                    )
                    listOf(
                        AddressFamilyMode.SMART to trx("Smart Dual-Stack"),
                        AddressFamilyMode.PREFER_IPV4 to trx("Prefer IPv4"),
                        AddressFamilyMode.PREFER_IPV6 to trx("Prefer IPv6"),
                        AddressFamilyMode.FORCE_IPV4 to trx("Force IPv4"),
                        AddressFamilyMode.FORCE_IPV6 to trx("Force IPv6")
                    ).forEach { (mode, label) ->
                        IosDivider()
                        IosChoiceRow(
                            title = label,
                            selected = s.addressFamilyMode == mode,
                            glyph = MarbleGlyph.ROUTE,
                            squircleColor = colors.blue,
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
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Strict Family Enforcement"),
                            subtitle = trx("Refuse servers that cannot use the forced IP version"),
                            checked = s.strictAddressFamily,
                            glyph = MarbleGlyph.SHIELD,
                            squircleColor = colors.orange,
                            onCheckedChange = { repo.updateSettings(s.copy(strictAddressFamily = it)) }
                        )
                    }
                }
            }
        }

        item(key = "dns-switches") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Encrypted DNS Protection"))
                IosCard {
                    if (s.connectionMode != ConnectionMode.FULL_TUN || !s.dnsHijackEnabled) {
                        IosSwitchRow(
                            title = trx("Intercept Port 53 DNS"),
                            subtitle = trx("Capture plaintext DNS queries inside the tunnel"),
                            checked = s.dnsHijackEnabled,
                            glyph = MarbleGlyph.SHIELD,
                            squircleColor = colors.green,
                            onCheckedChange = { repo.updateSettings(s.copy(dnsHijackEnabled = it)) }
                        )
                        IosDivider()
                    }
                    IosSwitchRow(
                        title = trx("Fake IP DNS"),
                        subtitle = trx("Return instant local synthetic IPs and resolve in-tunnel"),
                        checked = s.dnsFakeIpEnabled,
                        glyph = MarbleGlyph.SPARK,
                        squircleColor = colors.indigo,
                        onCheckedChange = { repo.updateSettings(s.copy(dnsFakeIpEnabled = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Adaptive DoH Ordering"),
                        subtitle = trx("Prefer the fastest healthy encrypted DNS endpoint"),
                        checked = s.adaptiveDnsEnabled,
                        glyph = MarbleGlyph.GAUGE,
                        squircleColor = colors.blue,
                        onCheckedChange = { repo.updateSettings(s.copy(adaptiveDnsEnabled = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Adaptive Dual-Stack DNS"),
                        subtitle = trx("Match DNS record family to active link capabilities"),
                        checked = s.adaptiveDualStackEnabled,
                        glyph = MarbleGlyph.LAYERS,
                        squircleColor = colors.teal,
                        onCheckedChange = { repo.updateSettings(s.copy(adaptiveDualStackEnabled = it)) }
                    )
                }
            }
        }

        item(key = "dns-resolvers") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("Resolvers"))
                IosChipStrip {
                    listOf(
                        Triple("Cloudflare", "1.1.1.1" to "1.0.0.1", "https://1.1.1.1/dns-query" to "https://1.0.0.1/dns-query"),
                        Triple("Google", "8.8.8.8" to "8.8.4.4", "https://8.8.8.8/dns-query" to "https://8.8.4.4/dns-query"),
                        Triple("Quad9", "9.9.9.9" to "149.112.112.112", "https://9.9.9.9/dns-query" to "https://149.112.112.112/dns-query")
                    ).forEach { (name, ips, dohs) ->
                        IosPillChip(
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

                IosCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IosTextField(
                                label = "TUN DNS 1",
                                value = s.dnsPrimaryIp,
                                onValueChange = { repo.updateSettings(s.copy(dnsPrimaryIp = it.trim())) },
                                modifier = Modifier.weight(1f)
                            )
                            IosTextField(
                                label = "TUN DNS 2",
                                value = s.dnsSecondaryIp,
                                onValueChange = { repo.updateSettings(s.copy(dnsSecondaryIp = it.trim())) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IosTextField(
                            label = trx("Primary DoH URL"),
                            value = s.dnsPrimaryDoH,
                            onValueChange = { repo.updateSettings(s.copy(dnsPrimaryDoH = it.trim())) }
                        )
                        IosTextField(
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
    val colors = IosTheme.colors
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

    IosSubPageScaffold(
        title = trx("Per-App Split Tunnel"),
        onBack = onBack
    ) {
        item(key = "split-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("App Tunnel Mode"))
                IosCard {
                    IosChoiceRow(
                        title = trx("All Apps"),
                        subtitle = trx("Every app on the device uses the VPN"),
                        selected = s.splitTunnelMode == SplitTunnelMode.ALL_APPS,
                        glyph = MarbleGlyph.GLOBE,
                        squircleColor = colors.blue,
                        onClick = { repo.updateSettings(s.copy(splitTunnelMode = SplitTunnelMode.ALL_APPS)) }
                    )
                    IosDivider()
                    IosChoiceRow(
                        title = trx("Only Selected Apps"),
                        subtitle = trx("Only checked apps below enter the VPN"),
                        selected = s.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED,
                        glyph = MarbleGlyph.CHECK,
                        squircleColor = colors.green,
                        onClick = { repo.updateSettings(s.copy(splitTunnelMode = SplitTunnelMode.ONLY_SELECTED)) }
                    )
                    IosDivider()
                    IosChoiceRow(
                        title = trx("Bypass Selected Apps"),
                        subtitle = trx("Checked apps go direct; all other apps use the VPN"),
                        selected = s.splitTunnelMode == SplitTunnelMode.BYPASS_SELECTED,
                        glyph = MarbleGlyph.ROUTE,
                        squircleColor = colors.purple,
                        onClick = { repo.updateSettings(s.copy(splitTunnelMode = SplitTunnelMode.BYPASS_SELECTED)) }
                    )
                }
            }
        }

        if (s.splitTunnelMode != SplitTunnelMode.ALL_APPS) {
            item(key = "split-apps") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IosSectionHeader(title = "${trx("Installed Apps")} (${selectedPackages.size} ${trx("selected")})")
                    IosSearchBar(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = trx("Search app or package name")
                    )
                    if (filteredApps.isNotEmpty()) {
                        IosCard {
                            filteredApps.take(80).forEachIndexed { idx, app ->
                                if (idx > 0) IosDivider()
                                val checked = app.packageName in selectedPackages
                                IosSwitchRow(
                                    title = app.label,
                                    subtitle = app.packageName,
                                    checked = checked,
                                    glyph = MarbleGlyph.FOLDER,
                                    squircleColor = if (checked) colors.purple else colors.gray,
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
    val colors = IosTheme.colors
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

    IosSubPageScaffold(
        title = trx("Iran Mode & Censorship Lab"),
        onBack = onBack
    ) {
        item(key = "iran-mode-policy") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Iran Mode"))
                IosCard {
                    listOf(
                        IranModePolicy.OFF to trx("Off"),
                        IranModePolicy.AUTO to trx("Automatic ISP Detection"),
                        IranModePolicy.ALWAYS_ON to trx("Always Active")
                    ).forEachIndexed { idx, (policy, label) ->
                        if (idx > 0) IosDivider()
                        IosChoiceRow(
                            title = label,
                            subtitle = if (policy == s.iranModePolicy && repo.iranMode.summary.isNotBlank()) {
                                repo.iranMode.summary
                            } else {
                                ""
                            },
                            selected = s.iranModePolicy == policy,
                            glyph = MarbleGlyph.SHIELD,
                            squircleColor = colors.red,
                            onClick = { repo.setIranModePolicy(policy) }
                        )
                    }
                    IosDivider()
                    IosSwitchRow(
                        title = trx("DPI Countermeasures"),
                        subtitle = trx("Enable anti-filtering timeouts, resolver hardening and MTU healing"),
                        checked = s.iranModeCountermeasures,
                        glyph = MarbleGlyph.SPARK,
                        squircleColor = colors.orange,
                        onCheckedChange = { repo.updateSettings(s.copy(iranModeCountermeasures = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Domestic Services Direct"),
                        subtitle = trx("Keep domestic sites direct when Iran Mode is active"),
                        checked = s.iranDomesticDirect,
                        glyph = MarbleGlyph.ROUTE,
                        squircleColor = colors.green,
                        onCheckedChange = { repo.updateSettings(s.copy(iranDomesticDirect = it)) }
                    )
                }
            }
        }

        item(key = "tls-fragmentation") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("TLS Handshake Fragmentation"))
                IosCard {
                    FragmentStrategy.entries.forEachIndexed { idx, strategy ->
                        if (idx > 0) IosDivider()
                        IosChoiceRow(
                            title = trx(strategy.label),
                            selected = fragment == strategy,
                            glyph = MarbleGlyph.LAYERS,
                            squircleColor = colors.indigo,
                            onClick = { repo.updateSettings(s.copy(fragmentStrategy = strategy.id)) }
                        )
                    }
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Auto-Apply Decisive Prescriptions"),
                        subtitle = trx("Apply recommended bypass settings immediately after a scan"),
                        checked = s.censorshipAutoApply,
                        glyph = MarbleGlyph.CHECK,
                        squircleColor = colors.teal,
                        onCheckedChange = { repo.updateSettings(s.copy(censorshipAutoApply = it)) }
                    )
                }
            }
        }

        item(key = "censorship-lab-scanner") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("Live Censorship Lab"))
                IosCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = trx("Runs bounded DNS, TCP and TLS handshake probes to identify active filtering on your current network."),
                            color = colors.subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                        IosButton(
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
                                CensorshipLab.Severity.CRITICAL -> colors.red
                                CensorshipLab.Severity.WARNING -> colors.orange
                                CensorshipLab.Severity.INFO -> colors.green
                            }
                            Text(
                                text = trx(diag.summary),
                                color = verdictColor,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            diag.findings.forEach { finding ->
                                Text(
                                    text = "• ${finding.technique.label}: ${finding.evidence}",
                                    color = colors.title,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            diag.observations.forEach { obs ->
                                Text(
                                    text = "${if (obs.reachable) "✓" else "✕"} ${obs.target} (${obs.kind.name}) ${if (obs.latencyMs > 0) "${obs.latencyMs}ms" else ""}",
                                    color = colors.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                            if (prescription != null && !prescription.empty) {
                                prescription.notes.forEach { note ->
                                    Text(
                                        text = "→ ${trx(note)}",
                                        color = colors.accent,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IosButton(
                                    label = trx("Apply Recommended Prescription"),
                                    glyph = MarbleGlyph.CHECK,
                                    style = IosButtonStyle.TINTED,
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
