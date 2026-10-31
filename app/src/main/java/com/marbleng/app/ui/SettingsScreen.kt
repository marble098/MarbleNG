@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.BuildConfig
import com.marbleng.app.core.AutoServerSelector
import com.marbleng.app.core.CoreEngineInfo
import com.marbleng.app.core.TunnelCoreInfo
import com.marbleng.app.core.coreEngine
import com.marbleng.app.core.tunnelCore
import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.JitterSmartMode
import com.marbleng.app.model.MapMotion
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
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleDefaultEffectsSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.panSpec
import com.marbleng.app.ui.theme.settleSpec
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

/**
 * MARBLE_SETTINGS_POSITION_V253 — the hub's place in the world, kept alive *above* the hub.
 *
 * The complaint was concrete: a reader filters to a section, opens one title, comes back — and the
 * app has dropped them at the top of "All" again. The hub used to own its search, its category and
 * its scroll inside its own composable, and any navigation into a detail (or a tab detour) threw
 * all three away. The state now lives in the root shell: leaving the hub for a title, a sibling
 * topic or another tab and returning lands the reader on the exact tile they left.
 */
class SettingsHubState internal constructor(
    private val searchState: MutableState<String>,
    private val categoryState: MutableState<String>,
    val listState: LazyListState
) {
    var search: String
        get() = searchState.value
        set(value) {
            searchState.value = value
        }

    var categoryRaw: String
        get() = categoryState.value
        set(value) {
            categoryState.value = value
        }
}

@Composable
fun rememberSettingsHubState(): SettingsHubState {
    val searchState = rememberSaveable { mutableStateOf("") }
    val categoryState = rememberSaveable { mutableStateOf(SettingsHubCategory.ALL.id) }
    val listState = rememberLazyListState()
    return remember(searchState, categoryState, listState) {
        SettingsHubState(searchState, categoryState, listState)
    }
}

@Composable
fun SettingsScreen(
    repo: AppRepository,
    activeDestination: SettingsDestination?,
    hubState: SettingsHubState,
    onOpenDestination: (SettingsDestination) -> Unit,
    onCloseDestination: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    // MARBLE_MAP_MOTION_V249 + MARBLE_EXPRESSIVE_STUDIO_V252:
    // Entering or leaving the hub uses the map depth zoom; switching between sibling topics inside
    // the same broader umbrella pans laterally on the shared expressive curve.
    val motion = marbleMotion()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    AnimatedContent(
        targetState = activeDestination,
        transitionSpec = {
            val from = initialState
            val to = targetState
            if (from != null && to != null &&
                SettingsHubLayout.umbrellaFor(from) == SettingsHubLayout.umbrellaFor(to)
            ) {
                val siblings = SettingsHubLayout.umbrellaFor(to).destinations
                val forward = siblings.indexOf(to) >= siblings.indexOf(from)
                val sign = when {
                    forward && !isRtl -> 1
                    !forward && isRtl -> 1
                    else -> -1
                }
                val panMs = MapMotion.durationMs(1)
                (
                    slideInHorizontally(motion.panSpec(panMs)) { full -> (full / 14) * sign } +
                        scaleIn(motion.panSpec(panMs), initialScale = 0.975f) +
                        fadeIn(motion.settleSpec(panMs))
                    ) togetherWith (
                    slideOutHorizontally(motion.panSpec(panMs)) { full -> -(full / 20) * sign } +
                        scaleOut(motion.panSpec(panMs), targetScale = 0.985f) +
                        fadeOut(motion.settleSpec(panMs / 2))
                    )
            } else {
                val duration = MapMotion.durationMs(1)
                val arrivingScale = if (to != null) {
                    MapMotion.DETAIL_ENTER_SCALE
                } else {
                    MapMotion.DETAIL_EXIT_SCALE
                }
                val departingScale = if (to != null) {
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
            }
        },
        label = "marbleSettingsDepth"
    ) { destination ->
        if (destination == null) {
            SettingsMenuPage(
                repo = repo,
                hubState = hubState,
                onOpenDestination = onOpenDestination,
                onContentScrollChanged = onContentScrollChanged
            )
        } else {
            val umbrellaState = SettingsUmbrellaNavState(
                group = SettingsHubLayout.umbrellaFor(destination),
                active = destination,
                onSelect = onOpenDestination
            )
            CompositionLocalProvider(LocalSettingsUmbrellaNav provides umbrellaState) {
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
}

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 — the cohesive Settings Bento hub.
 *
 * Instead of twenty-three separate micro-cards under stacked headings, the resting hub displays
 * seven broader categories in a single Material 3 Expressive Bento catalogue with contrast-paired
 * tonal tiles, distinct `MaterialShapes` polygons, and live status summaries. Typing in search
 * flattens across all twenty-three detailed destinations so any deep knob stays one tap away.
 */
@Composable
private fun SettingsMenuPage(
    repo: AppRepository,
    hubState: SettingsHubState,
    onOpenDestination: (SettingsDestination) -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tones = marbleExpressiveTones()
    val strings = Tr.now
    val s = repo.settings
    // MARBLE_SETTINGS_POSITION_V253 — the hub's search, section and scroll live in [hubState],
    // above the animated depth switch, so entering a title and coming back restores this exact
    // place instead of the top of "All".
    val listState = hubState.listState
    val search = hubState.search
    val categoryRaw = hubState.categoryRaw
    val category = SettingsHubCategory.fromId(categoryRaw)
    val searching = search.isNotBlank()

    LaunchedEffect(listState.isScrollInProgress) {
        onContentScrollChanged(listState.isScrollInProgress)
    }

    val modeSummary = if (s.connectionMode == ConnectionMode.FULL_TUN) {
        trx("Whole-phone VPN")
    } else {
        "${trx("Local proxy")} :${s.localProxyPort}"
    }
    val engineSummary = CoreEngineInfo.displayName(s.coreEngine())
    val tunnelSummary = TunnelCoreInfo.displayName(s.tunnelCore())
    val powerSummary = trx(parsePowerProfile(s.powerProfile).label)
    val routingSummary = when (s.routingMode) {
        RoutingMode.PROXY_ALL -> trx("Proxy all traffic")
        RoutingMode.BYPASS_PRIVATE -> trx("Bypass private LAN")
        RoutingMode.GEO_DIRECT -> trx("Iranian sites stay direct")
        RoutingMode.CUSTOM -> trx("Your own rules")
    }
    val splitSummary = when (s.splitTunnelMode) {
        SplitTunnelMode.ALL_APPS -> trx("All apps use VPN")
        SplitTunnelMode.ONLY_SELECTED -> trx("Only the apps you pick")
        SplitTunnelMode.BYPASS_SELECTED -> trx("The apps you pick stay direct")
    }
    val dnsSummary = "${s.dnsPrimaryIp} · " + trx(PlainVoice.addressFamilyLabel(s.addressFamilyMode.name))
    val pingSummary = when (s.probeMethod) {
        ProbeMethod.REAL_DELAY -> trx("Real tunnel delay")
        ProbeMethod.TCP_PING -> trx("Quick TCP ping")
        ProbeMethod.URL_TEST -> trx("Built-in core test")
    }
    val autoSummary = if (s.autoServerSelectorEnabled) {
        "${trx("On")} · " + trx(AutoServerSelector.shortLabel(s.autoServerStrategyEnum))
    } else {
        trx("Off · you pick the server")
    }
    val themeSummary = "${trx(s.theme.replaceFirstChar { it.uppercase() })} · ${trx(parseFlatAccent(s.accentTheme).label)}"
    val languageSummary = when (parseAppLanguage(s.appLanguage)) {
        AppLanguage.SYSTEM -> strings.languageSystem
        AppLanguage.ENGLISH -> strings.languageEnglish
        AppLanguage.PERSIAN -> strings.languagePersian
    }
    val subscriptionSummary = if (s.subscriptionAutoRefresh) {
        "${trx("Every")} ${faDigits("${s.subscriptionRefreshHours}")}h"
    } else {
        trx("Only when you ask")
    }

    val detailedCatalog = listOf(
        SettingsRowSpec(
            destination = SettingsDestination.CONNECTION_MODE,
            title = trx("How you connect"),
            subtitle = modeSummary,
            glyph = MarbleGlyph.TUNNEL,
            iconTint = colors.primary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.CONNECTION_MODE)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.CORE_ENGINE,
            title = trx("Proxy engine"),
            subtitle = engineSummary,
            glyph = MarbleGlyph.SERVER,
            iconTint = colors.secondary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.CORE_ENGINE)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.TUNNEL_DATAPATH,
            title = trx("Tunnel path"),
            subtitle = tunnelSummary,
            glyph = MarbleGlyph.LAYERS,
            iconTint = colors.tertiary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.TUNNEL_DATAPATH)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.POWER_PROFILE,
            title = trx("Speed and battery"),
            subtitle = powerSummary,
            glyph = MarbleGlyph.SPARK,
            iconTint = colors.warning,
            motif = SettingsHubLayout.motifFor(SettingsDestination.POWER_PROFILE)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.ROUTING,
            title = trx("What goes direct"),
            subtitle = routingSummary,
            glyph = MarbleGlyph.ROUTE,
            iconTint = colors.success,
            motif = SettingsHubLayout.motifFor(SettingsDestination.ROUTING)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.DNS_FAMILY,
            title = trx("DNS and IP version"),
            subtitle = dnsSummary,
            glyph = MarbleGlyph.GLOBE,
            iconTint = colors.tertiary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.DNS_FAMILY)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SPLIT_TUNNEL,
            title = trx("Which apps use the tunnel"),
            subtitle = splitSummary,
            glyph = MarbleGlyph.FOLDER,
            iconTint = colors.secondary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.SPLIT_TUNNEL)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.CENSORSHIP_LAB,
            title = trx("Iran mode & blocking test"),
            subtitle = trx("Find what blocks you and fix it"),
            glyph = MarbleGlyph.SHIELD,
            iconTint = colors.error,
            motif = SettingsHubLayout.motifFor(SettingsDestination.CENSORSHIP_LAB)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.PING_TESTING,
            title = trx("How ping is measured"),
            subtitle = pingSummary,
            glyph = MarbleGlyph.GAUGE,
            iconTint = colors.success,
            motif = SettingsHubLayout.motifFor(SettingsDestination.PING_TESTING)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.NETWORK_COUNCIL,
            title = trx("Network check"),
            subtitle = repo.councilReport?.let {
                "${trx("Last result")} · ${faDigits("${it.score}")} ${it.grade} · ${faDigits("${it.changes.size}")} " + trx("changes")
            } ?: trx("Twelve quick checks that tune the app for this network"),
            glyph = MarbleGlyph.RADAR,
            iconTint = colors.tertiary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.NETWORK_COUNCIL)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SMART_PRESETS,
            title = trx("One-tap modes"),
            subtitle = trx("Gaming, streaming or hard bypass in one tap"),
            glyph = MarbleGlyph.SPARK,
            iconTint = colors.warning,
            motif = SettingsHubLayout.motifFor(SettingsDestination.SMART_PRESETS)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.AUTO_SELECTOR,
            title = trx("Pick a server for me"),
            subtitle = autoSummary,
            glyph = MarbleGlyph.RADAR,
            iconTint = colors.primary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.AUTO_SELECTOR)
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
            iconTint = colors.success,
            motif = SettingsHubLayout.motifFor(SettingsDestination.SMART_JITTER)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.IDENTITY_LOCATION,
            title = trx("Hide my real IP"),
            subtitle = trx("Protect your IP and find server countries"),
            glyph = MarbleGlyph.GLOBE,
            iconTint = colors.secondary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.IDENTITY_LOCATION)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.APPEARANCE,
            title = trx("Look and colour"),
            subtitle = themeSummary,
            glyph = MarbleGlyph.PALETTE,
            iconTint = colors.secondary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.APPEARANCE)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.LANGUAGE_FONT,
            title = "${strings.languageTitle} & ${trx("Typography")}",
            subtitle = languageSummary,
            glyph = MarbleGlyph.FONT,
            iconTint = colors.secondary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.LANGUAGE_FONT)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.TAB_BAR,
            title = trx("Bottom bar & fourth tab"),
            subtitle = if (s.dockSlotEnabled) trx("Four tabs") else trx("Three tabs"),
            glyph = MarbleGlyph.DOCK,
            iconTint = colors.tertiary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.TAB_BAR)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.HOME_LAYOUT,
            title = strings.homeStyleTitle,
            subtitle = trx("Connect style, cards and shortcuts"),
            glyph = MarbleGlyph.HOME,
            iconTint = colors.warning,
            motif = SettingsHubLayout.motifFor(SettingsDestination.HOME_LAYOUT)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.SUBSCRIPTIONS_DATA,
            title = trx("Subscriptions & refresh"),
            subtitle = subscriptionSummary,
            glyph = MarbleGlyph.REFRESH,
            iconTint = colors.primary,
            motif = SettingsHubLayout.motifFor(SettingsDestination.SUBSCRIPTIONS_DATA)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.BACKUP_RESTORE,
            title = trx("Backup"),
            subtitle = trx("Save or restore everything in one file"),
            glyph = MarbleGlyph.VAULT,
            iconTint = colors.success,
            motif = SettingsHubLayout.motifFor(SettingsDestination.BACKUP_RESTORE)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.NOTIFICATIONS_BG,
            title = trx("Notifications & battery"),
            subtitle = trx("Live stats in the shade, and battery access"),
            glyph = MarbleGlyph.BELL,
            iconTint = colors.error,
            motif = SettingsHubLayout.motifFor(SettingsDestination.NOTIFICATIONS_BG)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.DIAGNOSTICS,
            title = trx("Fix & diagnose"),
            subtitle = trx("Checks that find and fix problems"),
            glyph = MarbleGlyph.TUNERS,
            iconTint = colors.warning,
            motif = SettingsHubLayout.motifFor(SettingsDestination.DIAGNOSTICS)
        ),
        SettingsRowSpec(
            destination = SettingsDestination.ABOUT,
            title = strings.informationTitle,
            subtitle = "v${BuildConfig.VERSION_NAME} · Xray ${BuildConfig.XRAY_CORE_TAG}",
            glyph = MarbleGlyph.INFO,
            iconTint = colors.onSurfaceVariant,
            motif = SettingsHubLayout.motifFor(SettingsDestination.ABOUT)
        )
    )

    val umbrellaSpecs = SettingsHubLayout.umbrellasForCategory(category).mapIndexed { index, group ->
        val pair = when (group) {
            SettingsUmbrellaGroup.APPEARANCE_UI -> tones.stage
            SettingsUmbrellaGroup.CONNECTION_CORE -> tones.sky
            SettingsUmbrellaGroup.ROUTING_BYPASS -> tones.mint
            SettingsUmbrellaGroup.PING_SMART -> tones.amber
            SettingsUmbrellaGroup.DNS_PRIVACY -> tones.lavender
            SettingsUmbrellaGroup.SUBSCRIPTIONS_BACKUP -> tones.plum
            SettingsUmbrellaGroup.SYSTEM_DIAGNOSTICS -> tones.plum
        }
        val liveSubtitle = when (group) {
            SettingsUmbrellaGroup.APPEARANCE_UI -> "$themeSummary · $languageSummary"
            SettingsUmbrellaGroup.CONNECTION_CORE -> "$modeSummary · $engineSummary"
            SettingsUmbrellaGroup.ROUTING_BYPASS -> "$routingSummary · $splitSummary"
            SettingsUmbrellaGroup.PING_SMART -> "$pingSummary · $autoSummary"
            SettingsUmbrellaGroup.DNS_PRIVACY -> dnsSummary
            SettingsUmbrellaGroup.SUBSCRIPTIONS_BACKUP -> "$subscriptionSummary · ${trx("Save or restore")}"
            SettingsUmbrellaGroup.SYSTEM_DIAGNOSTICS -> "v${BuildConfig.VERSION_NAME} · ${trx("Logs, battery & doctor")}"
        }
        val pills = group.destinations.take(3).map { dest ->
            trx(SettingsHubLayout.shortTopicTitleKey(dest))
        }
        SettingsRowSpec(
            destination = group.primaryDestination,
            title = trx(group.titleKey),
            subtitle = liveSubtitle,
            glyph = group.glyph,
            iconTint = pair.badge,
            motif = group.motif,
            indexBadge = faDigits((index + 1).toString().padStart(2, '0')),
            topicPills = pills,
            containerOverride = pair.container,
            contentOverride = pair.content
        )
    }

    val activeRows = if (searching) {
        val q = search.trim()
        detailedCatalog.filter { spec ->
            val group = SettingsHubLayout.umbrellaFor(spec.destination)
            spec.title.contains(q, ignoreCase = true) ||
                spec.subtitle.contains(q, ignoreCase = true) ||
                trx(group.titleKey).contains(q, ignoreCase = true) ||
                trx(SettingsHubLayout.shortTopicTitleKey(spec.destination)).contains(q, ignoreCase = true)
        }
    } else {
        umbrellaSpecs
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val columns = SettingsHubLayout.columns(maxWidth.value.toInt())
        val featured = SettingsHubLayout.featuredDestinations(activeRows, searching)
        val tileRows = SettingsHubLayout.rows(activeRows, columns, featured)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                top = 8.dp,
                bottom = MarbleTheme.bottomClearance
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    ) {
                        MarblePetalMark(
                            color = colors.primary.copy(alpha = 0.22f),
                            size = 36.dp,
                            motif = MarbleExpressiveMotif.FLOWER,
                            targetMotif = MarbleExpressiveMotif.SUNNY
                        )
                    }
                    MarbleSearchField(
                        value = search,
                        onValueChange = { hubState.search = it },
                        placeholder = trx("Search settings"),
                        trailingCount = if (searching) {
                            faDigits("${activeRows.size}")
                        } else {
                            faDigits("${SettingsUmbrellaGroup.entries.size}")
                        }
                    )
                    if (searching && activeRows.isEmpty()) {
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
                        SettingsHubCategory.visible.forEach { entry ->
                            SettingsCategoryChip(
                                category = entry,
                                selected = entry == category,
                                onClick = { hubState.categoryRaw = entry.id }
                            )
                        }
                    }
                }
            }

            items(
                count = tileRows.size,
                key = { index -> "settings-bento-row-${if (searching) "search" else category.id}-$index" }
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
                                motif = spec.motif,
                                indexBadge = spec.indexBadge,
                                topicPills = spec.topicPills,
                                containerOverride = spec.containerOverride,
                                contentOverride = spec.contentOverride,
                                featured = spec.destination in featured,
                                onClick = { onOpenDestination(spec.destination) },
                                modifier = Modifier.weight(1f),
                                tag = "settings-tile-${spec.destination}"
                            )
                        }
                    }
                }
            }

            item(key = "settings-footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MarbleNG · v${BuildConfig.VERSION_NAME} · Xray ${BuildConfig.XRAY_CORE_TAG}",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
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
    val shape = marbleSelectionMorphShape(
        selected = selected,
        idleDp = 14.dp,
        selectedDp = 28.dp
    )
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        animationSpec = marbleDefaultEffectsSpec(),
        label = "settingsCategoryBg"
    )
    val ink = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val motif = when (category) {
        SettingsHubCategory.ALL -> MarbleExpressiveMotif.FLOWER
        SettingsHubCategory.CONNECTION -> MarbleExpressiveMotif.FAN
        SettingsHubCategory.NETWORK -> MarbleExpressiveMotif.SUNNY
        else -> MarbleExpressiveMotif.DIAMOND
    }
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(container)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .testTag("settings-category-${category.id}")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 18.dp else 10.dp)
                .clip(marbleExpressiveShape(motif))
                .background(if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.CHECK,
                    color = colors.onPrimary,
                    modifier = Modifier.size(11.dp),
                    weight = 1.25f
                )
            }
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
            val motif = when (preset) {
                SmartPreset.GAMING -> MarbleExpressiveMotif.SUNNY
                SmartPreset.STREAMING -> MarbleExpressiveMotif.FAN
                SmartPreset.EXTREME_BYPASS -> MarbleExpressiveMotif.CLOVER
            }
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(marbleExpressiveShape(motif))
                            .background(
                                (if (selected) colors.primary else colors.secondary).copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MarbleGlyphIcon(
                            glyph = presetGlyph(preset),
                            color = if (selected) colors.primary else colors.secondary,
                            modifier = Modifier.size(22.dp),
                            weight = 1.1f
                        )
                    }
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
