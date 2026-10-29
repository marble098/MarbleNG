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
import com.marbleng.app.model.ConnectButtonStyle
import com.marbleng.app.model.DOCK_SLOT_CAPTION_MAX
import com.marbleng.app.model.DockPresentation
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockSlotAccent
import com.marbleng.app.model.DockSlotIcon
import com.marbleng.app.model.DockSlotKind
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.model.TextScale
import com.marbleng.app.model.dockShowsIcons
import com.marbleng.app.model.dockShowsLabels
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseAppLanguage
import com.marbleng.app.model.parseConnectButtonStyle
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
        title = trx("Appearance & Accent"),
        onBack = onBack
    ) {
        item(key = "theme-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = Tr.now.themeTitle)
                MarbleCard {
                    listOf(
                        Triple("light", trx("Light"), trx("Bright surfaces with dark text")),
                        Triple("dark", trx("Dark"), trx("True-dark surfaces, kind to OLED screens")),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlatAccent.entries.forEach { candidate ->
                            val swatch = marbleAccentPrimary(candidate, resolveMarbleDarkMode(s.theme, isSystemInDarkTheme()))
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
                                        repo.updateSettings(s.copy(appBehavior = s.appBehavior.copy(accentTheme = candidate.id)))
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
        title = trx("Tab Bar & Fourth Tab"),
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
    val connectStyle = parseConnectButtonStyle(s.connectButtonStyle)

    MarbleSubPageScaffold(
        title = Tr.now.homeStyleTitle,
        onBack = onBack
    ) {
        item(key = "home-connect-style") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Connect Control Style"))
                MarbleCard {
                    MarbleChoiceRow(
                        title = trx("Split connect button"),
                        subtitle = trx("Tap the status emblem or the big Connect half; the small half changes route"),
                        selected = connectStyle != ConnectButtonStyle.SLIDE,
                        glyph = MarbleGlyph.POWER,
                        iconTint = colors.success,
                        onClick = { repo.updateSettings(s.copy(connectButtonStyle = ConnectButtonStyle.ROUND.id)) }
                    )
                    MarbleDivider()
                    MarbleChoiceRow(
                        title = trx("Slide to connect"),
                        subtitle = trx("Swipe horizontally across the pill track to connect or disconnect"),
                        selected = connectStyle == ConnectButtonStyle.SLIDE,
                        glyph = MarbleGlyph.CHEVRON,
                        iconTint = colors.primary,
                        onClick = { repo.updateSettings(s.copy(connectButtonStyle = ConnectButtonStyle.SLIDE.id)) }
                    )
                }
            }
        }

        item(key = "home-cards-visibility") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Visible Home Cards"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Network Speed & Session Data"),
                        subtitle = trx("Show live throughput and session/total usage card"),
                        checked = s.modularShowStats,
                        glyph = MarbleGlyph.GAUGE,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(modularShowStats = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Local SOCKS5 Row"),
                        subtitle = trx("Always display the loopback SOCKS5 address on Home"),
                        checked = s.modularShowSocks,
                        glyph = MarbleGlyph.LINK,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(modularShowSocks = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Quick Actions Card"),
                        subtitle = trx("Show clipboard import, QR scanner, Smart Rank and Censorship Lab"),
                        checked = s.modularShowShortcuts,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.warning,
                        onCheckedChange = { repo.updateSettings(s.copy(modularShowShortcuts = it)) }
                    )
                }
            }
        }
    }
}
