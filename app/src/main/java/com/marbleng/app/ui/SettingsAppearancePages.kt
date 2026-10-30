package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.AppFont
import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.DOCK_SLOT_CAPTION_MAX
import com.marbleng.app.model.DockPresentation
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockSlotAccent
import com.marbleng.app.model.DockSlotIcon
import com.marbleng.app.model.DockSlotKind
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.model.HomeSection
import com.marbleng.app.model.HomeStudio
import com.marbleng.app.model.TextScale
import com.marbleng.app.model.dockShowsIcons
import com.marbleng.app.model.dockShowsLabels
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseAppLanguage
import com.marbleng.app.model.encodeDockPreference
import com.marbleng.app.model.parseDockPresentation
import com.marbleng.app.model.parseDockSize
import com.marbleng.app.model.parseDockSlotAccent
import com.marbleng.app.model.parseDockSlotIcon
import com.marbleng.app.model.parseDockSlotKind
import com.marbleng.app.model.parseFlatAccent
import kotlin.math.roundToInt
import com.marbleng.app.ui.theme.marbleAccentPrimary
import com.marbleng.app.ui.theme.resolveMarbleDarkMode
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

/**
 * MARBLE_HOME_STUDIO_V249 — swatches per line. Five 48dp circles plus their spacing fit a narrow
 * phone; the ninth accent wraps to a second line instead of being clipped off the edge.
 */
private const val ACCENT_SWATCHES_PER_ROW: Int = 5

@Composable
fun SettingsAppearancePage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val haptics = LocalHapticFeedback.current
    val activeAccent = parseFlatAccent(s.accentTheme)

    MarbleSubPageScaffold(
        title = trx("Look and colour"),
        onBack = onBack
    ) {
        item(key = "theme-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = Tr.now.themeTitle)
                MarbleCard {
                    // MARBLE_HOME_STUDIO_V249 — four themes, not three. `amoled` was already a
                    // theme id the resolver understood and nothing in the app could select, so the
                    // darkest thing this product could draw was a value with no door to it.
                    listOf(
                        Triple("light", trx("Light"), trx("Bright surfaces with dark text")),
                        Triple("dark", trx("Dark"), trx("True-dark surfaces, kind to OLED screens")),
                        Triple(
                            "amoled",
                            trx("True Black"),
                            trx("The page itself is black, so an OLED screen turns those pixels off")
                        ),
                        Triple("system", trx("Follow System"), trx("Switch automatically with device appearance"))
                    ).forEachIndexed { idx, (id, title, sub) ->
                        if (idx > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = title,
                            subtitle = sub,
                            selected = s.theme.equals(id, ignoreCase = true),
                            glyph = MarbleGlyph.PALETTE,
                            iconTint = when (id) {
                                "light" -> colors.primary
                                "dark" -> colors.secondary
                                "amoled" -> colors.onSurfaceVariant
                                else -> colors.tertiary
                            },
                            onClick = { repo.updateSettings(s.copy(theme = id)) }
                        )
                    }
                }
            }
        }

        item(key = "theme-preview") {
            // Council: live preview — WYSIWYG for theme + accent
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Preview"))
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(colors.primary),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            MarbleGlyphIcon(glyph = MarbleGlyph.PALETTE, color = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = trx("MarbleNG Card Preview"),
                                color = colors.onSurface,
                                // MARBLE_WEIGHT_LADDER_V235 — the preview shows a card title, so
                                // it previews the TITLE rung real cards now use, not Bold.
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${trx(s.theme.replaceFirstChar { it.uppercase() })} · ${trx(activeAccent.label)}",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        MarbleBadge(text = "Aa", color = colors.primary)
                    }
                }
            }
        }

        item(key = "accent-swatches") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Accent Color"))
                MarbleCard(contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // MARBLE_HOME_STUDIO_V249 — nine swatches, five to a line. One row of nine
                        // 48dp circles is 432dp on a 360dp screen: the last accents would have been
                        // clipped, and a colour the user cannot see is not a choice.
                        FlatAccent.entries.chunked(ACCENT_SWATCHES_PER_ROW).forEach { swatchRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                swatchRow.forEach { candidate ->
                                    val swatch = marbleAccentPrimary(
                                        candidate,
                                        resolveMarbleDarkMode(s.theme, isSystemInDarkTheme())
                                    )
                                    val chosen = activeAccent == candidate
                                    val swatchDescription = trx(candidate.label)
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .then(
                                                if (chosen) {
                                                    Modifier.border(2.5.dp, colors.onSurface, CircleShape)
                                                } else {
                                                    Modifier.border(1.dp, colors.outlineVariant, CircleShape)
                                                }
                                            )
                                            .semantics {
                                                contentDescription = swatchDescription
                                            }
                                            .clickable(role = Role.Button) {
                                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                repo.updateSettings(
                                                    s.copy(
                                                        appBehavior = s.appBehavior.copy(
                                                            accentTheme = candidate.id
                                                        )
                                                    )
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (chosen) {
                                            MarbleGlyphIcon(
                                                glyph = MarbleGlyph.CHECK,
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                weight = 1.25f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "dynamic-color") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Wallpaper Colors"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Dynamic Color"),
                        subtitle = trx("Match the wallpaper palette on Android 12+; the accent above is the fallback"),
                        checked = s.dynamicColor,
                        glyph = MarbleGlyph.PALETTE,
                        iconTint = colors.tertiary,
                        onCheckedChange = {
                            repo.updateSettings(s.copy(appBehavior = s.appBehavior.copy(dynamicColor = it)))
                        }
                    )
                }
            }
        }

        item(key = "app-motion") {
            // MARBLE_MAP_MOTION_V249 — one switch over the whole app's motion.
            //
            // It scales every duration and every travel distance the app owns (the tab map's pan, the
            // uptime ticker's roll, the dock's flight, a drill-in to a setting) rather than switching
            // animations off: a reader who finds the movement distracting usually still wants to see
            // where a page came from. Android's own "remove animations" scale keeps working on top of
            // it, and that one wins — when the phone says still, the app renders the resting frame.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Motion"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Calm motion"),
                        subtitle = trx("Shorter, smaller transitions everywhere — the same choreography at six tenths the distance"),
                        checked = s.appCalmMotion,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(appCalmMotion = it)) }
                    )
                }
                MarbleSectionFooter(
                    text = trx("If Android's own animation setting is off, the app stays still whatever this says")
                )
            }
        }

        item(key = "surface-depth") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Card Elevation"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Flat surfaces"),
                        subtitle = trx("Low-contrast tonal cards without elevation shadows"),
                        checked = s.flatInterface,
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(flatInterface = it)) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsLanguageAndFontPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val s = repo.settings
    val lang = parseAppLanguage(s.appLanguage)
    val font = parseAppFont(s.fontFamily)

    MarbleSubPageScaffold(
        title = "${strings.languageTitle} & ${trx("Typography")}",
        onBack = onBack
    ) {
        item(key = "app-language") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = strings.languageTitle)
                MarbleCard {
                    listOf(
                        AppLanguage.SYSTEM to (strings.languageSystem to strings.languageSystemDetail),
                        AppLanguage.ENGLISH to (strings.languageEnglish to trx("Left-to-right English interface")),
                        AppLanguage.PERSIAN to (strings.languagePersian to trx("Right-to-left Persian interface"))
                    ).forEachIndexed { idx, (candidate, labels) ->
                        if (idx > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = labels.first,
                            subtitle = labels.second,
                            selected = lang == candidate,
                            glyph = MarbleGlyph.GLOBE,
                            iconTint = colors.primary,
                            onClick = { repo.updateSettings(s.copy(appLanguage = candidate.id)) }
                        )
                    }
                }
            }
        }

        item(key = "app-font") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Typeface"))
                MarbleCard {
                    AppFont.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = candidate.label,
                            selected = font == candidate,
                            glyph = MarbleGlyph.FONT,
                            iconTint = colors.secondary,
                            onClick = { repo.updateSettings(s.copy(fontFamily = candidate.id)) }
                        )
                    }
                }
            }
        }

        item(key = "text-scale") {
            val scale = TextScale.clamp(s.textScalePercent)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = "${trx("Text Size")} · ${faDigits("$scale%")}")
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Slider(
                            value = scale.toFloat(),
                            onValueChange = { v ->
                                repo.updateSettings(
                                    s.copy(textScalePercent = TextScale.snapped(v.roundToInt())),
                                    coalesceWrite = true
                                )
                            },
                            valueRange = TextScale.MIN_PERCENT.toFloat()..TextScale.MAX_PERCENT.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = colors.outlineVariant
                            )
                        )
                        MarbleChipStrip {
                            TextScale.STEPS.forEach { preset ->
                                MarblePillChip(
                                    label = "$preset%",
                                    selected = scale == preset,
                                    onClick = { repo.updateSettings(s.copy(textScalePercent = preset)) }
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
fun SettingsTabBarPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val dockSize = parseDockSize(s.dockSize)
    val dockPresentation = parseDockPresentation(s.dockSize)
    val slotKind = parseDockSlotKind(s.dockSlotKind)
    val slotIcon = parseDockSlotIcon(s.dockSlotIcon)
    val slotAccent = parseDockSlotAccent(s.dockSlotAccent)
    val showLabels = dockShowsLabels(s.dockShowIcons, s.dockShowLabels)
    val showIcons = dockShowsIcons(s.dockShowIcons, s.dockShowLabels)

    MarbleSubPageScaffold(
        title = trx("Bottom bar & fourth tab"),
        onBack = onBack
    ) {
        item(key = "tabbar-chrome") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Tab Bar Appearance"))
                MarbleSegmentedControl(
                    options = listOf(
                        DockSize.SMALL to trx("Compact"),
                        DockSize.MEDIUM to trx("Standard"),
                        DockSize.LARGE to trx("Large")
                    ),
                    selected = dockSize,
                    onSelect = {
                        repo.updateSettings(
                            s.copy(dockSize = encodeDockPreference(it, parseDockPresentation(s.dockSize)))
                        )
                    }
                )
                MarbleSectionHeader(title = trx("Bottom Bar Position"))
                MarbleSegmentedControl(
                    options = listOf(
                        DockPresentation.ATTACHED to trx("Attached to bottom"),
                        DockPresentation.FLOATING to trx("Floating above bottom")
                    ),
                    selected = dockPresentation,
                    onSelect = {
                        repo.updateSettings(
                            s.copy(dockSize = encodeDockPreference(dockSize, it))
                        )
                    }
                )
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Show Tab Labels"),
                        checked = showLabels,
                        glyph = MarbleGlyph.FONT,
                        iconTint = colors.primary,
                        onCheckedChange = { enabled ->
                            repo.updateSettings(
                                s.copy(
                                    dockShowLabels = enabled,
                                    dockShowIcons = if (!enabled) true else s.dockShowIcons
                                )
                            )
                        }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Show Tab Icons"),
                        checked = showIcons,
                        glyph = MarbleGlyph.DOCK,
                        iconTint = colors.secondary,
                        onCheckedChange = { enabled ->
                            repo.updateSettings(
                                s.copy(
                                    dockShowIcons = enabled,
                                    dockShowLabels = if (!enabled) true else s.dockShowLabels
                                )
                            )
                        }
                    )
                }
            }
        }

        item(key = "fourth-tab-config") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Fourth Tab Shortcut"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Show Fourth Tab"),
                        subtitle = trx("Add a dedicated Pulse, Subscription or Server tab to the bottom bar"),
                        checked = s.dockSlotEnabled,
                        glyph = MarbleGlyph.DOCK,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(dockSlotEnabled = it)) }
                    )
                    listOf(
                        DockSlotKind.PULSE to trx("Live Pulse & Diagnostics"),
                        DockSlotKind.SOURCE to trx("Pinned Subscription Group"),
                        DockSlotKind.CONFIG to trx("Pinned Single Server")
                    ).forEach { (candidate, label) ->
                        MarbleDivider()
                        MarbleChoiceRow(
                            title = label,
                            selected = slotKind == candidate,
                            glyph = MarbleGlyph.SPARK,
                            iconTint = colors.primary,
                            onClick = { repo.updateSettings(s.copy(dockSlotKind = candidate.id)) }
                        )
                    }
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Live Connection Dot"),
                        subtitle = trx("Show active status indicator on the fourth tab"),
                        checked = s.dockSlotShowStatusBadge,
                        glyph = MarbleGlyph.CHECK,
                        iconTint = colors.success,
                        onCheckedChange = { repo.updateSettings(s.copy(dockSlotShowStatusBadge = it)) }
                    )
                }

                if (slotKind == DockSlotKind.SOURCE) {
                    val sourceOptions = buildList {
                        add(MarbleMenuOption("all", Tr.now.homeAllGroups))
                        repo.subscriptions.forEach { add(MarbleMenuOption(it.id, it.name)) }
                        add(MarbleMenuOption("manual", Tr.now.homeManualGroup))
                    }
                    MarbleDropdownField(
                        label = trx("Pinned Subscription Group"),
                        value = s.dockSlotSourceId.ifBlank { "all" },
                        options = sourceOptions,
                        onSelect = { repo.updateSettings(s.copy(dockSlotSourceId = it)) }
                    )
                } else if (slotKind == DockSlotKind.CONFIG && repo.libraryProfiles.isNotEmpty()) {
                    val profileOptions = repo.libraryProfiles.map { p ->
                        MarbleMenuOption(
                            value = "${p.subscriptionId}|${p.id}",
                            label = cleanServerName(p.name),
                            detail = "${protocolShortLabel(p)} · ${p.subscriptionName}"
                        )
                    }
                    val currentKey = "${s.dockSlotProfileSourceId}|${s.dockSlotProfileId}"
                    MarbleDropdownField(
                        label = trx("Pinned Server"),
                        value = currentKey,
                        options = profileOptions,
                        onSelect = { raw ->
                            val parts = raw.split('|', limit = 2)
                            if (parts.size == 2) {
                                repo.updateSettings(
                                    s.copy(
                                        dockSlotProfileSourceId = parts[0],
                                        dockSlotProfileId = parts[1]
                                    )
                                )
                            }
                        }
                    )
                }

                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MarbleTextField(
                            label = trx("Custom Tab Label (optional)"),
                            value = s.dockSlotLabel,
                            onValueChange = { repo.updateSettings(s.copy(dockSlotLabel = it.take(DOCK_SLOT_CAPTION_MAX))) }
                        )
                        MarbleChipStrip {
                            DockSlotIcon.entries.forEach { ic ->
                                MarblePillChip(
                                    label = ic.id.uppercase(),
                                    selected = slotIcon == ic,
                                    onClick = { repo.updateSettings(s.copy(dockSlotIcon = ic.id)) }
                                )
                            }
                        }
                        MarbleChipStrip {
                            DockSlotAccent.entries.forEach { ac ->
                                MarblePillChip(
                                    label = ac.id.uppercase(),
                                    selected = slotAccent == ac,
                                    accent = dockSlotColor(ac),
                                    onClick = { repo.updateSettings(s.copy(dockSlotAccent = ac.id)) }
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
fun SettingsHomeLayoutPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings

    MarbleSubPageScaffold(
        title = Tr.now.homeStyleTitle,
        onBack = onBack
    ) {
        item(key = "home-connect-style") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Connect Control"))
                MarbleCard(contentPadding = PaddingValues(18.dp)) {
                    // MARBLE_EXPRESSIVE_SHELL_V241 — the style choice is gone because the
                    // control it chose between is gone. There is one pill, one mode chip and
                    // one status chip on Home; this page describes that instead of offering a
                    // setting that no longer changes anything.
                    Text(
                        text = trx("One pill connects and disconnects. The chip under it picks Smart, Full TUN or Local proxy. The slide track was removed: a swipe that had to land in the right half was a second thing to learn for one action."),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // MARBLE_HOME_STUDIO_V249 — every section Home draws, with a switch and two move buttons.
        //
        // Three switches became six sections plus an order, because "hide the speed card" was never
        // really the ask: the ask was that Home is a layout the user owns. Two things this list has
        // to be honest about, and both are why it looks the way it does:
        //
        //   * A section that is off still shows its row. A switch that removes itself cannot be
        //     turned back on, so the row stays and dims instead.
        //   * Turning everything off is allowed, and the page says so out loud. An empty Home is a
        //     choice somebody might actually make (a pill and nothing else), and a product that
        //     quietly refuses it is a product that knows better than its user.
        item(key = "home-sections-studio") {
            val order = HomeStudio.order(s.homeSectionOrder)
            val hiddenCount = HomeStudio.hiddenCount(
                hiddenRaw = s.homeHiddenSections,
                legacyStatsVisible = s.modularShowStats,
                legacyShortcutsVisible = s.modularShowShortcuts
            )
            val bare = HomeStudio.isBare(
                orderRaw = s.homeSectionOrder,
                hiddenRaw = s.homeHiddenSections,
                legacyStatsVisible = s.modularShowStats,
                legacyShortcutsVisible = s.modularShowShortcuts
            )

            fun sectionGlyph(section: HomeSection): MarbleGlyph = when (section) {
                HomeSection.HEADER -> MarbleGlyph.HOME
                HomeSection.CONNECT -> MarbleGlyph.POWER
                HomeSection.PRESETS -> MarbleGlyph.SPARK
                HomeSection.ROUTE -> MarbleGlyph.ROUTE
                HomeSection.STATS -> MarbleGlyph.GAUGE
                HomeSection.SHORTCUTS -> MarbleGlyph.MENU
            }

            // One write per change, and the two legacy switches are written with it: the studio's
            // record and the record an older build reads must never disagree about the same section.
            fun setSectionVisible(section: HomeSection, visible: Boolean) {
                val hidden = HomeStudio.hiddenIds(s.homeHiddenSections).toMutableSet()
                if (visible) hidden -= section.id else hidden += section.id
                repo.updateSettings(
                    s.copy(
                        homeHiddenSections = HomeStudio.serializeHidden(hidden),
                        modularShowStats = if (section == HomeSection.STATS) {
                            visible
                        } else {
                            s.modularShowStats
                        },
                        modularShowShortcuts = if (section == HomeSection.SHORTCUTS) {
                            visible
                        } else {
                            s.modularShowShortcuts
                        }
                    )
                )
            }

            fun moveSection(section: HomeSection, steps: Int) {
                val moved = HomeStudio.move(s.homeSectionOrder, section, steps) ?: return
                repo.updateSettings(s.copy(homeSectionOrder = moved))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Home sections"),
                    badge = faDigits("${order.size - hiddenCount}/${order.size}"),
                    badgeColor = if (hiddenCount == 0) colors.success else colors.warning
                )
                MarbleCard {
                    order.forEachIndexed { index, section ->
                        if (index > 0) MarbleDivider()
                        val hidden = HomeStudio.isHidden(
                            section = section,
                            hiddenRaw = s.homeHiddenSections,
                            legacyStatsVisible = s.modularShowStats,
                            legacyShortcutsVisible = s.modularShowShortcuts
                        )
                        val upLabel = trx("Move up")
                        val downLabel = trx("Move down")
                        MarbleListItem(
                            title = trx(section.title),
                            subtitle = trx(section.hint),
                            glyph = sectionGlyph(section),
                            iconTint = if (hidden) colors.onSurfaceVariant else colors.primary,
                            titleColor = if (hidden) colors.onSurfaceVariant else colors.onSurface,
                            showChevron = false,
                            onClick = { setSectionVisible(section, hidden) },
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MarbleIconButton(
                                        glyph = MarbleGlyph.CHEVRON_DOWN,
                                        onClick = { moveSection(section, -1) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .rotate(180f),
                                        tint = colors.onSurfaceVariant,
                                        enabled = HomeStudio.canMove(s.homeSectionOrder, section, -1),
                                        contentDescription = upLabel
                                    )
                                    MarbleIconButton(
                                        glyph = MarbleGlyph.CHEVRON_DOWN,
                                        onClick = { moveSection(section, 1) },
                                        modifier = Modifier.size(32.dp),
                                        tint = colors.onSurfaceVariant,
                                        enabled = HomeStudio.canMove(s.homeSectionOrder, section, 1),
                                        contentDescription = downLabel
                                    )
                                    MarbleSwitch(
                                        checked = !hidden,
                                        onCheckedChange = { setSectionVisible(section, it) }
                                    )
                                }
                            }
                        )
                    }
                }
                if (bare) {
                    MarbleSectionFooter(
                        text = trx("Home is empty right now. Turn one section back on, or keep it as a single pill.")
                    )
                } else {
                    MarbleSectionFooter(
                        text = trx("The order here is the order Home draws them in, top to bottom")
                    )
                }
                MarbleButton(
                    label = trx("Reset Home layout"),
                    style = MarbleButtonStyle.SECONDARY,
                    onClick = {
                        repo.updateSettings(
                            s.copy(
                                homeSectionOrder = HomeStudio.resetOrder(),
                                homeHiddenSections = HomeStudio.resetHidden(),
                                modularShowStats = true,
                                modularShowShortcuts = true
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item(key = "home-cards-visibility") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Inside the sections"))
                MarbleCard {
                    // The SOCKS5 row is a line *inside* the speed section, not a section of its own,
                    // so it keeps its own switch here instead of a row in the list above.
                    MarbleSwitchRow(
                        title = trx("Local SOCKS5 Row"),
                        subtitle = trx("Always display the loopback SOCKS5 address on Home"),
                        checked = s.modularShowSocks,
                        glyph = MarbleGlyph.LINK,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(modularShowSocks = it)) }
                    )
                }
            }
        }
    }
}
