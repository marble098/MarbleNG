package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.marbleng.app.AppRepository
import com.marbleng.app.BuildConfig
import com.marbleng.app.core.AutoServerSelector
import com.marbleng.app.core.CoreEngineInfo
import com.marbleng.app.core.TunnelCoreInfo
import com.marbleng.app.core.coreEngine
import com.marbleng.app.core.tunnelCore
import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.MapMotion
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.JitterSmartMode
import com.marbleng.app.model.PlainVoice
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
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.panSpec
import com.marbleng.app.ui.theme.settleSpec
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

@Composable
fun SettingsScreen(
    repo: AppRepository,
    activeDestination: SettingsDestination?,
    onOpenDestination: (SettingsDestination) -> Unit,
    onCloseDestination: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    // MARBLE_MAP_MOTION_V249 — opening a setting is a zoom into the map, and going back is the same
    // zoom reversed.
    //
    // This used to be an early return: `if (activeDestination != null) { …page…; return }` and then
    // the menu, which meant the two could never be on screen at the same time and neither could
    // move. The cost of making them one `AnimatedContent` is that the menu becomes its own
    // composable ([SettingsMenuPage]); the gain is that a drill-in and a drill-out are the same
    // movement in opposite directions, on the same curve and at the same tempo as the tab map one
    // level up — which is what makes the app read as one surface instead of a stack of screens.
    val motion = marbleMotion()
    AnimatedContent(
        targetState = activeDestination,
        transitionSpec = {
            val duration = MapMotion.durationMs(1)
            val arrivingScale = if (targetState != null) {
                MapMotion.DETAIL_ENTER_SCALE
            } else {
                MapMotion.DETAIL_EXIT_SCALE
            }
            val departingScale = if (targetState != null) {
                MapMotion.DETAIL_EXIT_SCALE
            } else {
                MapMotion.DETAIL_ENTER_SCALE
            }
            (
                fadeIn(motion.settleSpec(duration)) + scaleIn(
                    animationSpec = motion.panSpec(duration),
                    initialScale = arrivingScale
                )
                ) togetherWith (
                fadeOut(motion.settleSpec(duration)) + scaleOut(
                    animationSpec = motion.panSpec(duration),
                    targetScale = departingScale
                )
                )
        },
        label = "marbleSettingsDepth"
    ) { destination ->
        if (destination == null) {
            SettingsMenuPage(
                repo = repo,
                onOpenDestination = onOpenDestination,
                onContentScrollChanged = onContentScrollChanged
            )
        } else {
            when (destination) {
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
        }
    }
}

/**
 * MARBLE_MAP_MOTION_V249 — the Settings menu, extracted unchanged so [SettingsScreen] can cross
 * between it and a sub-page instead of returning early into one or the other.
 */
@Composable
private fun SettingsMenuPage(
    repo: AppRepository,
    onOpenDestination: (SettingsDestination) -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {

    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val s = repo.settings
    val listState = rememberLazyListState()
    var search by rememberSaveable { mutableStateOf("") }
    var categoryRaw by rememberSaveable { mutableStateOf(SettingsHubCategory.ALL.id) }
    val category = SettingsHubCategory.fromId(categoryRaw)
    val searching = search.isNotBlank()

    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    val connectionGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.CONNECTION_MODE,
            title = trx("How you connect"),
            subtitle = if (s.connectionMode == ConnectionMode.FULL_TUN) {
                trx("Whole-phone VPN")
            } else {
                "${trx("Local proxy")} :${s.localProxyPort}"
            },
            glyph = MarbleGlyph.TUNNEL,
            iconTint = colors.primary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.CORE_ENGINE,
            title = trx("Proxy engine"),
            subtitle = CoreEngineInfo.displayName(s.coreEngine()),
            glyph = MarbleGlyph.SERVER,
            iconTint = colors.secondary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.TUNNEL_DATAPATH,
            title = trx("Tunnel path"),
            subtitle = TunnelCoreInfo.displayName(s.tunnelCore()),
            glyph = MarbleGlyph.LAYERS,
            iconTint = colors.tertiary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.POWER_PROFILE,
            title = trx("Speed and battery"),
            subtitle = trx(parsePowerProfile(s.powerProfile).label),
            glyph = MarbleGlyph.SPARK,
            iconTint = colors.warning
        )
    )

    val routingGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.ROUTING,
            title = trx("What goes direct"),
            subtitle = when (s.routingMode) {
                RoutingMode.PROXY_ALL -> trx("Proxy all traffic")
                RoutingMode.BYPASS_PRIVATE -> trx("Bypass private LAN")
                RoutingMode.GEO_DIRECT -> trx("Iranian sites stay direct")
                RoutingMode.CUSTOM -> trx("Your own rules")
            },
            glyph = MarbleGlyph.ROUTE,
            iconTint = colors.success
        ),
        SettingsRowSpec(
            destination = SettingsDestination.DNS_FAMILY,
            title = trx("DNS and IP version"),
            subtitle = "${s.dnsPrimaryIp} · " + trx(PlainVoice.addressFamilyLabel(s.addressFamilyMode.name)),
            glyph = MarbleGlyph.GLOBE,
            iconTint = colors.tertiary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SPLIT_TUNNEL,
            title = trx("Which apps use the tunnel"),
            subtitle = when (s.splitTunnelMode) {
                SplitTunnelMode.ALL_APPS -> trx("All apps use VPN")
                SplitTunnelMode.ONLY_SELECTED -> trx("Only the apps you pick")
                SplitTunnelMode.BYPASS_SELECTED -> trx("The apps you pick stay direct")
            },
            glyph = MarbleGlyph.FOLDER,
            iconTint = colors.secondary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.CENSORSHIP_LAB,
            title = trx("Iran mode & blocking test"),
            subtitle = trx("Find what blocks you and fix it"),
            glyph = MarbleGlyph.SHIELD,
            iconTint = colors.error
        )
    )

    val testingGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.PING_TESTING,
            title = trx("How ping is measured"),
            subtitle = when (s.probeMethod) {
                ProbeMethod.REAL_DELAY -> trx("Real tunnel delay")
                ProbeMethod.TCP_PING -> trx("Quick TCP ping")
                ProbeMethod.URL_TEST -> trx("Built-in core test")
            },
            glyph = MarbleGlyph.GAUGE,
            iconTint = colors.success
        ),
        SettingsRowSpec(
            destination = SettingsDestination.NETWORK_COUNCIL,
            title = trx("Network check"),
            subtitle = repo.councilReport?.let {
                "${trx("Last result")} · ${it.score} ${it.grade} · ${it.changes.size} " + trx("changes")
            } ?: trx("Twelve quick checks that tune the app for this network"),
            glyph = MarbleGlyph.RADAR,
            iconTint = colors.tertiary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SMART_PRESETS,
            title = trx("One-tap modes"),
            subtitle = trx("Gaming, streaming or hard bypass in one tap"),
            glyph = MarbleGlyph.SPARK,
            iconTint = colors.warning
        ),
        SettingsRowSpec(
            destination = SettingsDestination.AUTO_SELECTOR,
            title = trx("Pick a server for me"),
            subtitle = if (s.autoServerSelectorEnabled) {
                "${trx("On")} · " + trx(AutoServerSelector.shortLabel(s.autoServerStrategyEnum))
            } else {
                trx("Off · you pick the server")
            },
            glyph = MarbleGlyph.RADAR,
            iconTint = colors.primary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SMART_JITTER,
            title = trx("Smooth out the jitter"),
            subtitle = if (s.jitterSmartModeEnum == JitterSmartMode.OFF) {
                trx("Off · nothing is changed")
            } else {
                "${trx("On")} · ${trx(s.jitterSmartModeEnum.label)}"
            },
            glyph = MarbleGlyph.GAUGE,
            iconTint = colors.success
        ),
        SettingsRowSpec(
            destination = SettingsDestination.IDENTITY_LOCATION,
            title = trx("Hide my real IP"),
            subtitle = trx("Protect your IP and find server countries"),
            glyph = MarbleGlyph.GLOBE,
            iconTint = colors.secondary
        )
    )

    val appearanceGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.APPEARANCE,
            title = trx("Look and colour"),
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
            title = trx("Bottom bar & fourth tab"),
            subtitle = if (s.dockSlotEnabled) {
                trx("Four tabs")
            } else {
                trx("Three tabs")
            },
            glyph = MarbleGlyph.DOCK,
            iconTint = colors.tertiary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.HOME_LAYOUT,
            title = strings.homeStyleTitle,
            subtitle = trx("Connect style, cards and shortcuts"),
            glyph = MarbleGlyph.HOME,
            iconTint = colors.warning
        )
    )

    val systemGroup = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.SUBSCRIPTIONS_DATA,
            title = trx("Subscriptions & refresh"),
            subtitle = if (s.subscriptionAutoRefresh) {
                "${trx("Every")} ${s.subscriptionRefreshHours}h"
            } else {
                trx("Only when you ask")
            },
            glyph = MarbleGlyph.REFRESH,
            iconTint = colors.primary
        ),
        SettingsRowSpec(
            destination = SettingsDestination.BACKUP_RESTORE,
            title = trx("Backup"),
            subtitle = trx("Save or restore everything in one file"),
            glyph = MarbleGlyph.VAULT,
            iconTint = colors.success
        ),
        SettingsRowSpec(
            destination = SettingsDestination.NOTIFICATIONS_BG,
            title = trx("Notifications & battery"),
            subtitle = trx("Live stats in the shade, and battery access"),
            glyph = MarbleGlyph.BELL,
            iconTint = colors.error
        ),
        SettingsRowSpec(
            destination = SettingsDestination.DIAGNOSTICS,
            title = trx("Fix & diagnose"),
            subtitle = trx("Checks that find and fix problems"),
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

    val categorizedSections = listOf(
        SettingsHubCategory.CONNECTION to (strings.categoryConnection to connectionGroup),
        SettingsHubCategory.NETWORK to (strings.categoryNetwork to routingGroup),
        SettingsHubCategory.TESTS to (strings.categoryTests to testingGroup),
        SettingsHubCategory.APPEARANCE to (strings.categoryAppearance to appearanceGroup),
        SettingsHubCategory.SYSTEM to (strings.categorySystem to systemGroup)
    )
    val sections = categorizedSections.mapNotNull { (sectionCategory, pair) ->
        val (header, items) = pair
        val filtered = when {
            searching -> {
                val q = search.trim()
                items.filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.subtitle.contains(q, ignoreCase = true)
                }
            }
            category == SettingsHubCategory.ALL || category == sectionCategory -> items
            else -> emptyList()
        }
        if (filtered.isEmpty()) null else Triple(sectionCategory, header, filtered)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // MARBLE_EXPRESSIVE_PAGES_V251 — the shell's ambient light is this page's ground.
            .statusBarsPadding()
    ) {
        val columns = SettingsHubLayout.columns(maxWidth.value.toInt())
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
                        subtitle = trx("Your network, your way"),
                        collapseFraction = collapseFractionOf(
                            firstVisibleItemIndex = listState.firstVisibleItemIndex,
                            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
                        )
                    )
                    MarbleSearchField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = trx("Search settings"),
                        trailingCount = if (searching) "" else faDigits("${connectionGroup.size + routingGroup.size + testingGroup.size + appearanceGroup.size + systemGroup.size}")
                    )
                    if (searching && sections.isEmpty()) {
                        MarbleEmptyState(
                            title = trx("No matches"),
                            body = trx("Try a shorter keyword — e.g. DNS, Theme, or Ping"),
                            glyph = MarbleGlyph.SEARCH,
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }

            if (!searching) {
                item(key = "settings-categories") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsHubCategory.entries.forEach { entry ->
                            SettingsCategoryChip(
                                category = entry,
                                selected = entry == category,
                                onClick = { categoryRaw = entry.id }
                            )
                        }
                    }
                }
            }

            sections.forEach { (sectionCategory, header, rows) ->
                item(key = "settings-sec-${sectionCategory.id}") {
                    MarbleSectionHeader(
                        title = header,
                        badge = faDigits("${rows.size}"),
                        badgeColor = colors.primary
                    )
                }
                val featured = SettingsHubLayout.featuredDestinations(rows, searching)
                val tileRows = SettingsHubLayout.rows(rows, columns, featured)
                items(
                    count = tileRows.size,
                    key = { index -> "settings-row-${sectionCategory.id}-$index" }
                ) { index ->
                    val row = tileRows[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { spec ->
                            if (spec == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                MarbleExpressiveTile(
                                    title = spec.title,
                                    subtitle = spec.subtitle,
                                    glyph = spec.glyph,
                                    tint = spec.iconTint,
                                    featured = spec.destination in featured,
                                    onClick = { onOpenDestination(spec.destination) },
                                    modifier = Modifier.weight(1f),
                                    tag = "settings-tile-${spec.destination}"
                                )
                            }
                        }
                    }
                }
            }

            item(key = "settings-footer") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                        text = trx("Tap a row to change it • Search to find one"),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryChip(
    category: SettingsHubCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val container = if (selected) colors.primaryContainer else colors.surfaceContainerLow
    val ink = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .testTag("settings-category-${category.id}")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) {
            MarbleGlyphIcon(
                glyph = MarbleGlyph.CHECK,
                color = ink,
                modifier = Modifier.size(16.dp),
                weight = 1.2f
            )
        }
        Text(
            text = trx(category.titleKey),
            color = ink,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
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

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.SPARK,
        tint = colors.warning,
        title = trx("One-tap modes"),
        subtitle = trx("Gaming, streaming or hard bypass in one tap"),
        onBack = onBack
    ) {
        item(key = "presets-intro") {
            MarbleSectionFooter(
                trx("Pick a mode and the app tunes itself. Your servers, routing, DNS and tunnel stay as they are.")
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
