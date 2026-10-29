package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.BuildConfig
import com.marbleng.app.core.CoreEngineInfo
import com.marbleng.app.core.TunnelCoreInfo
import com.marbleng.app.core.coreEngine
import com.marbleng.app.core.tunnelCore
import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.JitterSmartMode
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.SmartPreset
import com.marbleng.app.model.SplitTunnelMode
import com.marbleng.app.model.autoServerStrategyEnum
import com.marbleng.app.model.jitterSmartModeEnum
import com.marbleng.app.model.matchSmartPreset
import com.marbleng.app.model.parseAppLanguage
import com.marbleng.app.model.parseFlatAccent
import com.marbleng.app.model.parsePowerProfile
import androidx.compose.material3.MaterialTheme
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

enum class SettingsDestination {
    CONNECTION_MODE,
    CORE_ENGINE,
    TUNNEL_DATAPATH,
    POWER_PROFILE,
    ROUTING,
    DNS_FAMILY,
    SPLIT_TUNNEL,
    CENSORSHIP_LAB,
    PING_TESTING,
    NETWORK_COUNCIL,
    AUTO_SELECTOR,
    SMART_PRESETS,
    SMART_JITTER,
    IDENTITY_LOCATION,
    APPEARANCE,
    LANGUAGE_FONT,
    TAB_BAR,
    HOME_LAYOUT,
    SUBSCRIPTIONS_DATA,
    BACKUP_RESTORE,
    NOTIFICATIONS_BG,
    DIAGNOSTICS,
    ABOUT
}

private data class SettingsRowSpec(
    val destination: SettingsDestination,
    val title: String,
    val subtitle: String,
    val glyph: MarbleGlyph,
    val iconTint: Color
)

@Composable
fun SettingsScreen(
    repo: AppRepository,
    activeDestination: SettingsDestination?,
    onOpenDestination: (SettingsDestination) -> Unit,
    onCloseDestination: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    if (activeDestination != null) {
        when (activeDestination) {
            SettingsDestination.CONNECTION_MODE -> SettingsConnectionModePage(repo, onCloseDestination)
            SettingsDestination.CORE_ENGINE -> SettingsCoreEnginePage(repo, onCloseDestination)
            SettingsDestination.TUNNEL_DATAPATH -> SettingsTunnelDatapathPage(repo, onCloseDestination)
            SettingsDestination.POWER_PROFILE -> SettingsPowerProfilePage(repo, onCloseDestination)
            SettingsDestination.ROUTING -> SettingsRoutingPage(repo, onCloseDestination)
            SettingsDestination.DNS_FAMILY -> SettingsDnsAndFamilyPage(repo, onCloseDestination)
            SettingsDestination.SPLIT_TUNNEL -> SettingsSplitTunnelPage(repo, onCloseDestination)
            SettingsDestination.CENSORSHIP_LAB -> SettingsCensorshipLabPage(repo, onCloseDestination)
            SettingsDestination.PING_TESTING -> SettingsPingTestingPage(repo, onCloseDestination)
            SettingsDestination.NETWORK_COUNCIL -> SettingsNetworkCouncilPage(repo, onCloseDestination)
            SettingsDestination.AUTO_SELECTOR -> SettingsAutoSelectorPage(repo, onCloseDestination)
            SettingsDestination.SMART_PRESETS -> SettingsSmartPresetsPage(repo, onCloseDestination)
            SettingsDestination.SMART_JITTER -> SettingsSmartJitterPage(repo, onCloseDestination)
            SettingsDestination.IDENTITY_LOCATION -> SettingsIdentityLocationPage(repo, onCloseDestination)
            SettingsDestination.APPEARANCE -> SettingsAppearancePage(repo, onCloseDestination)
            SettingsDestination.LANGUAGE_FONT -> SettingsLanguageAndFontPage(repo, onCloseDestination)
            SettingsDestination.TAB_BAR -> SettingsTabBarPage(repo, onCloseDestination)
            SettingsDestination.HOME_LAYOUT -> SettingsHomeLayoutPage(repo, onCloseDestination)
            SettingsDestination.SUBSCRIPTIONS_DATA -> SettingsSubscriptionsPage(repo, onCloseDestination)
            SettingsDestination.BACKUP_RESTORE -> SettingsBackupRestorePage(repo, onCloseDestination)
            SettingsDestination.NOTIFICATIONS_BG -> SettingsNotificationsPage(repo, onCloseDestination)
            SettingsDestination.DIAGNOSTICS -> SettingsDiagnosticsPage(repo, onCloseDestination)
            SettingsDestination.ABOUT -> SettingsAboutPage(repo, onCloseDestination)
        }
        return
    }

    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val s = repo.settings
    val listState = rememberLazyListState()
    var search by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    val connectionGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.CONNECTION_MODE,
            title = trx("Connection Mode & Ports"),
            subtitle = if (s.connectionMode == ConnectionMode.FULL_TUN) {
                trx("Full TUN device VPN")
            } else {
                "${trx("Local SOCKS5 proxy")} :${s.localProxyPort}"
            },
            glyph = MarbleGlyph.TUNNEL,
            iconTint = colors.primary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.CORE_ENGINE,
            title = trx("Proxy Core Engine"),
            subtitle = CoreEngineInfo.displayName(s.coreEngine()),
            glyph = MarbleGlyph.SERVER,
            iconTint = colors.secondary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.TUNNEL_DATAPATH,
            title = trx("Tunnel Datapath"),
            subtitle = TunnelCoreInfo.displayName(s.tunnelCore()),
            glyph = MarbleGlyph.LAYERS,
            iconTint = colors.tertiary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.POWER_PROFILE,
            title = trx("Power & Speed Profile"),
            subtitle = trx(parsePowerProfile(s.powerProfile).label),
            glyph = MarbleGlyph.SPARK,
            iconTint = colors.warning
        )
    )

    val routingGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.ROUTING,
            title = trx("Routing & Geo Databases"),
            subtitle = when (s.routingMode) {
                RoutingMode.PROXY_ALL -> trx("Proxy all traffic")
                RoutingMode.BYPASS_PRIVATE -> trx("Bypass private LAN")
                RoutingMode.GEO_DIRECT -> trx("Geo direct domestic separation")
                RoutingMode.CUSTOM -> trx("Custom routing rules")
            },
            glyph = MarbleGlyph.ROUTE,
            iconTint = colors.success
        ),
        SettingsRowSpec(
            destination = SettingsDestination.DNS_FAMILY,
            title = trx("DNS & IPv4 / IPv6"),
            subtitle = "${s.dnsPrimaryIp} · ${s.addressFamilyMode.name}",
            glyph = MarbleGlyph.GLOBE,
            iconTint = colors.tertiary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SPLIT_TUNNEL,
            title = trx("Per-App Split Tunnel"),
            subtitle = when (s.splitTunnelMode) {
                SplitTunnelMode.ALL_APPS -> trx("All apps use VPN")
                SplitTunnelMode.ONLY_SELECTED -> trx("Only selected apps use VPN")
                SplitTunnelMode.BYPASS_SELECTED -> trx("Selected apps bypass VPN")
            },
            glyph = MarbleGlyph.FOLDER,
            iconTint = colors.secondary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.CENSORSHIP_LAB,
            title = trx("Iran Mode & Censorship Lab"),
            subtitle = trx("Detect DPI filtering, TLS resets and apply prescriptions"),
            glyph = MarbleGlyph.SHIELD,
            iconTint = colors.error
        )
    )

    val testingGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.PING_TESTING,
            title = trx("Ping & Delay Testing"),
            subtitle = when (s.probeMethod) {
                ProbeMethod.REAL_DELAY -> trx("Real HTTPS tunnel delay")
                ProbeMethod.TCP_PING -> trx("TCP handshake ping")
                ProbeMethod.URL_TEST -> trx("Native core URL test")
            },
            glyph = MarbleGlyph.GAUGE,
            iconTint = colors.success
        ),
        SettingsRowSpec(
            destination = SettingsDestination.NETWORK_COUNCIL,
            title = trx("Network Council Scanner"),
            subtitle = repo.councilReport?.let {
                "${trx("Last verdict")} · ${it.score} ${it.grade} · ${it.changes.size} " + trx("settings")
            } ?: trx("12 experts measure this link, then apply the best settings"),
            glyph = MarbleGlyph.RADAR,
            iconTint = colors.tertiary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SMART_PRESETS,
            title = trx("Smart Presets"),
            subtitle = trx("Gaming, Streaming and Extreme Bypass in one tap"),
            glyph = MarbleGlyph.SPARK,
            iconTint = colors.warning
        ),
        SettingsRowSpec(
            destination = SettingsDestination.AUTO_SELECTOR,
            title = trx("Automatic Server Selector"),
            subtitle = if (s.autoServerSelectorEnabled) {
                "${trx("On")} · ${s.autoServerStrategyEnum.name}"
            } else {
                trx("Off · manual server selection")
            },
            glyph = MarbleGlyph.RADAR,
            iconTint = colors.primary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SMART_JITTER,
            title = trx("Smart Jitter Reducer"),
            subtitle = if (s.jitterSmartModeEnum == JitterSmartMode.OFF) {
                trx("Off · the network policy stays untouched")
            } else {
                "${trx("On")} · ${trx(s.jitterSmartModeEnum.label)}"
            },
            glyph = MarbleGlyph.GAUGE,
            iconTint = colors.success
        ),
        SettingsRowSpec(
            destination = SettingsDestination.IDENTITY_LOCATION,
            title = trx("Identity Guard & Locations"),
            subtitle = trx("Exit IP protection and automatic country detection"),
            glyph = MarbleGlyph.GLOBE,
            iconTint = colors.secondary
        )
    )

    val appearanceGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.APPEARANCE,
            title = trx("Appearance & Accent"),
            subtitle = "${trx(s.theme.replaceFirstChar { it.uppercase() })} · ${trx(parseFlatAccent(s.accentTheme).label)}",
            glyph = MarbleGlyph.PALETTE,
            iconTint = colors.secondary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.LANGUAGE_FONT,
            title = "${strings.languageTitle} & ${trx("Typography")}",
            subtitle = when (parseAppLanguage(s.appLanguage)) {
                AppLanguage.SYSTEM -> strings.languageSystem
                AppLanguage.ENGLISH -> strings.languageEnglish
                AppLanguage.PERSIAN -> strings.languagePersian
            },
            glyph = MarbleGlyph.FONT,
            iconTint = colors.secondary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.TAB_BAR,
            title = trx("Tab Bar & Fourth Tab"),
            subtitle = if (s.dockSlotEnabled) {
                trx("Four tabs enabled")
            } else {
                trx("Three tabs")
            },
            glyph = MarbleGlyph.DOCK,
            iconTint = colors.tertiary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.HOME_LAYOUT,
            title = strings.homeStyleTitle,
            subtitle = trx("Connect button style, telemetry cards and shortcuts"),
            glyph = MarbleGlyph.HOME,
            iconTint = colors.warning
        )
    )

    val systemGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.SUBSCRIPTIONS_DATA,
            title = trx("Subscriptions & Auto-Refresh"),
            subtitle = if (s.subscriptionAutoRefresh) {
                "${trx("Every")} ${s.subscriptionRefreshHours}h"
            } else {
                trx("Manual refresh only")
            },
            glyph = MarbleGlyph.REFRESH,
            iconTint = colors.primary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.BACKUP_RESTORE,
            title = trx("Backup & Restore"),
            subtitle = trx("Export or import all servers, subscriptions and settings"),
            glyph = MarbleGlyph.VAULT,
            iconTint = colors.success
        ),
        SettingsRowSpec(
            destination = SettingsDestination.NOTIFICATIONS_BG,
            title = trx("Notifications & Background"),
            subtitle = trx("Live notification stats and unrestricted battery access"),
            glyph = MarbleGlyph.BELL,
            iconTint = colors.error
        ),
        SettingsRowSpec(
            destination = SettingsDestination.DIAGNOSTICS,
            title = trx("Diagnostics, Logs & Bug Finder"),
            subtitle = trx("Privacy audit, system doctor, core logs and health checks"),
            glyph = MarbleGlyph.TUNERS,
            iconTint = colors.warning
        ),
        SettingsRowSpec(
            destination = SettingsDestination.ABOUT,
            title = strings.informationTitle,
            subtitle = "v${BuildConfig.VERSION_NAME} · Xray ${BuildConfig.XRAY_CORE_TAG}",
            glyph = MarbleGlyph.INFO,
            iconTint = colors.onSurfaceVariant
        )
    )

    val sections = listOf(
        strings.categoryConnection to connectionGroup,
        strings.categoryNetwork to routingGroup,
        strings.categoryTests to testingGroup,
        strings.categoryAppearance to appearanceGroup,
        strings.categorySystem to systemGroup
    ).mapNotNull { (header, items) ->
        val filtered = if (search.isBlank()) {
            items
        } else {
            val q = search.trim()
            items.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.subtitle.contains(q, ignoreCase = true)
            }
        }
        if (filtered.isEmpty()) null else header to filtered
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            top = 8.dp,
            bottom = MarbleTheme.bottomClearance
        ),
        verticalArrangement = Arrangement.spacedBy(MarbleSpec.SECTION_SPACING_DP.dp)
    ) {
        item(key = "settings-header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MarbleLargeHeader(
                    title = strings.settingsTitle,
                    collapseFraction = collapseFractionOf(
                        firstVisibleItemIndex = listState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
                    )
                )
                MarbleSearchField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = trx("Search settings"),
                    trailingCount = if (search.isBlank()) faDigits("${connectionGroup.size + routingGroup.size + testingGroup.size + appearanceGroup.size + systemGroup.size}") else ""
                )
                if (search.isNotBlank() && sections.isEmpty()) {
                    MarbleEmptyState(
                        title = trx("No matches"),
                        body = trx("Try a shorter keyword — e.g. DNS, Theme, or Ping"),
                        glyph = MarbleGlyph.SEARCH,
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }

        sections.forEachIndexed { sectionIndex, (header, rows) ->
            item(key = "settings-sec-$sectionIndex") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleSectionHeader(title = header, badge = faDigits("${rows.size}"), badgeColor = colors.primary)
                    MarbleCard {
                        rows.forEachIndexed { rowIndex, spec ->
                            if (rowIndex > 0) MarbleDivider()
                            MarbleListItem(
                                title = spec.title,
                                subtitle = spec.subtitle,
                                glyph = spec.glyph,
                                iconTint = spec.iconTint,
                                onClick = { onOpenDestination(spec.destination) }
                            )
                        }
                    }
                }
            }
        }
        // Council: footer legibility
        item(key = "settings-footer") {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME} · Xray ${BuildConfig.XRAY_CORE_TAG} · MarbleNG",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Text(
                    text = trx("Tap any row to configure • Use search to jump"),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * MARBLE_EXPRESSIVE_SHELL_V241 — the page behind the Home preset strip.
 *
 * The same three decisions, with their consequences written out, and a derived check mark on
 * whichever one the current settings already match. Applying a preset writes tuning knobs only
 * (see [SmartPreset.apply]); routing, DNS, the split tunnel and the selected server are untouched.
 */
@Composable
internal fun SettingsSmartPresetsPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val active = matchSmartPreset(repo.settings)

    MarbleSubPageScaffold(
        title = trx("Smart Presets"),
        onBack = onBack
    ) {
        item(key = "presets-intro") {
            MarbleSectionFooter(
                trx("A preset bundles the tuning knobs behind a named decision. Your servers, routing, DNS and split tunnel are never changed.")
            )
        }
        items(SmartPreset.ordered) { preset ->
            val selected = preset == active
            val appliedLabel = trx(preset.title) + " • " + trx("applied")
            MarbleCard(
                modifier = Modifier.clickable {
                    repo.updateSettings(preset.apply(repo.settings))
                    repo.setRuntimeMessage(appliedLabel)
                },
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MarbleRowIcon(
                        glyph = presetGlyph(preset),
                        tint = if (selected) colors.primary else colors.secondary
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = trx(preset.title),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = trx(preset.body),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (selected) {
                        MarbleGlyphIcon(
                            glyph = MarbleGlyph.CHECK,
                            color = colors.primary,
                            modifier = Modifier.size(18.dp),
                            weight = 1.15f
                        )
                    }
                }
            }
        }
    }
}
