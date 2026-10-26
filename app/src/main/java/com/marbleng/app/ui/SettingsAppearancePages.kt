package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import com.marbleng.app.model.parseDockPresentation
import com.marbleng.app.model.parseDockSize
import com.marbleng.app.model.parseDockSlotAccent
import com.marbleng.app.model.parseDockSlotIcon
import com.marbleng.app.model.parseDockSlotKind
import com.marbleng.app.model.parseFlatAccent
import kotlin.math.roundToInt

@Composable
fun SettingsAppearancePage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val s = repo.settings
    val activeAccent = parseFlatAccent(s.accentTheme)

    IosSubPageScaffold(
        title = trx("Appearance & Accent"),
        onBack = onBack
    ) {
        item(key = "theme-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = Tr.now.themeTitle)
                IosCard {
                    listOf(
                        Triple("light", trx("iOS Light"), trx("Crisp #F8F9FA background with #FFFFFF rounded cards")),
                        Triple("dark", trx("iOS Dark"), trx("True dark OLED background with #1C1C1E cards")),
                        Triple("system", trx("Follow System"), trx("Switch automatically with device appearance"))
                    ).forEachIndexed { idx, (id, title, sub) ->
                        if (idx > 0) IosDivider()
                        IosChoiceRow(
                            title = title,
                            subtitle = sub,
                            selected = s.theme.equals(id, ignoreCase = true),
                            glyph = MarbleGlyph.PALETTE,
                            squircleColor = when (id) {
                                "light" -> colors.blue
                                "dark" -> colors.indigo
                                else -> colors.teal
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
                IosSectionHeader(title = trx("Preview"))
                IosCard(contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(colors.accent),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            MarbleGlyphIcon(glyph = MarbleGlyph.PALETTE, color = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = trx("MarbleNG Card Preview"),
                                color = colors.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${trx(s.theme.replaceFirstChar { it.uppercase() })} · ${trx(activeAccent.label)}",
                                color = colors.subtitle,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IosBadge(text = "Aa", color = colors.accent)
                    }
                }
            }
        }

        item(key = "accent-swatches") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("Accent Color"))
                IosCard(contentPadding = PaddingValues(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlatAccent.entries.forEach { candidate ->
                            val swatch = iosAccentColor(candidate, dark = colors.dark)
                            val chosen = activeAccent == candidate
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .then(
                                        if (chosen) {
                                            Modifier.border(2.5.dp, colors.title, CircleShape)
                                        } else {
                                            Modifier.border(1.dp, colors.cardBorder, CircleShape)
                                        }
                                    )
                                    .clickable(role = Role.Button) {
                                        repo.updateSettings(s.copy(accentTheme = candidate.id))
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

        item(key = "surface-depth") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Card Elevation"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Flat Card Surfaces"),
                        subtitle = trx("Disable even the subtle 2dp card shadow for pure flat iOS geometry"),
                        checked = s.flatInterface,
                        glyph = MarbleGlyph.LAYERS,
                        squircleColor = colors.pink,
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
    val colors = IosTheme.colors
    val strings = Tr.now
    val s = repo.settings
    val lang = parseAppLanguage(s.appLanguage)
    val font = parseAppFont(s.fontFamily)

    IosSubPageScaffold(
        title = "${strings.languageTitle} & ${trx("Typography")}",
        onBack = onBack
    ) {
        item(key = "app-language") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = strings.languageTitle)
                IosCard {
                    listOf(
                        AppLanguage.SYSTEM to (strings.languageSystem to strings.languageSystemDetail),
                        AppLanguage.ENGLISH to (strings.languageEnglish to "Left-to-right English interface"),
                        AppLanguage.PERSIAN to (strings.languagePersian to "رابط کاربری راست‌به‌چپ فارسی")
                    ).forEachIndexed { idx, (candidate, labels) ->
                        if (idx > 0) IosDivider()
                        IosChoiceRow(
                            title = labels.first,
                            subtitle = labels.second,
                            selected = lang == candidate,
                            glyph = MarbleGlyph.GLOBE,
                            squircleColor = colors.blue,
                            onClick = { repo.updateSettings(s.copy(appLanguage = candidate.id)) }
                        )
                    }
                }
            }
        }

        item(key = "app-font") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Typeface"))
                IosCard {
                    AppFont.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) IosDivider()
                        IosChoiceRow(
                            title = candidate.label,
                            selected = font == candidate,
                            glyph = MarbleGlyph.FONT,
                            squircleColor = colors.purple,
                            onClick = { repo.updateSettings(s.copy(fontFamily = candidate.id)) }
                        )
                    }
                }
            }
        }

        item(key = "text-scale") {
            val scale = TextScale.clamp(s.textScalePercent)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = "${trx("Text Size")} · $scale%")
                IosCard(contentPadding = PaddingValues(16.dp)) {
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
                                thumbColor = colors.accent,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.divider
                            )
                        )
                        IosChipStrip {
                            TextScale.STEPS.forEach { preset ->
                                IosPillChip(
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
    val colors = IosTheme.colors
    val s = repo.settings
    val dockSize = parseDockSize(s.dockSize)
    val dockPresentation = parseDockPresentation(s.dockPresentation)
    val slotKind = parseDockSlotKind(s.dockSlotKind)
    val slotIcon = parseDockSlotIcon(s.dockSlotIcon)
    val slotAccent = parseDockSlotAccent(s.dockSlotAccent)
    val showLabels = dockShowsLabels(s.dockShowIcons, s.dockShowLabels)
    val showIcons = dockShowsIcons(s.dockShowIcons, s.dockShowLabels)

    IosSubPageScaffold(
        title = trx("Tab Bar & Fourth Tab"),
        onBack = onBack
    ) {
        item(key = "tabbar-chrome") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("Tab Bar Appearance"))
                IosSegmentedControl(
                    options = listOf(
                        DockSize.SMALL to trx("Compact"),
                        DockSize.MEDIUM to trx("Standard"),
                        DockSize.LARGE to trx("Large")
                    ),
                    selected = dockSize,
                    onSelect = { repo.updateSettings(s.copy(dockSize = it.id)) }
                )
                IosSectionHeader(title = trx("Bottom Bar Position"))
                IosSegmentedControl(
                    options = listOf(
                        DockPresentation.ATTACHED to trx("Attached to bottom"),
                        DockPresentation.FLOATING to trx("Floating above bottom")
                    ),
                    selected = dockPresentation,
                    onSelect = { repo.updateSettings(s.copy(dockPresentation = it.id)) }
                )
                IosCard {
                    IosSwitchRow(
                        title = trx("Show Tab Labels"),
                        checked = showLabels,
                        glyph = MarbleGlyph.FONT,
                        squircleColor = colors.blue,
                        onCheckedChange = { enabled ->
                            repo.updateSettings(
                                s.copy(
                                    dockShowLabels = enabled,
                                    dockShowIcons = if (!enabled) true else s.dockShowIcons
                                )
                            )
                        }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Show Tab Icons"),
                        checked = showIcons,
                        glyph = MarbleGlyph.DOCK,
                        squircleColor = colors.indigo,
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
                IosSectionHeader(title = trx("Fourth Tab Shortcut"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Show Fourth Tab"),
                        subtitle = trx("Add a dedicated Pulse, Subscription or Server tab to the bottom bar"),
                        checked = s.dockSlotEnabled,
                        glyph = MarbleGlyph.DOCK,
                        squircleColor = colors.teal,
                        onCheckedChange = { repo.updateSettings(s.copy(dockSlotEnabled = it)) }
                    )
                    listOf(
                        DockSlotKind.PULSE to trx("Live Pulse & Diagnostics"),
                        DockSlotKind.SOURCE to trx("Pinned Subscription Group"),
                        DockSlotKind.CONFIG to trx("Pinned Single Server")
                    ).forEach { (candidate, label) ->
                        IosDivider()
                        IosChoiceRow(
                            title = label,
                            selected = slotKind == candidate,
                            glyph = MarbleGlyph.SPARK,
                            squircleColor = colors.blue,
                            onClick = { repo.updateSettings(s.copy(dockSlotKind = candidate.id)) }
                        )
                    }
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Live Connection Dot"),
                        subtitle = trx("Show active status indicator on the fourth tab"),
                        checked = s.dockSlotShowStatusBadge,
                        glyph = MarbleGlyph.CHECK,
                        squircleColor = colors.green,
                        onCheckedChange = { repo.updateSettings(s.copy(dockSlotShowStatusBadge = it)) }
                    )
                }

                if (slotKind == DockSlotKind.SOURCE) {
                    val sourceOptions = buildList {
                        add(IosMenuOption("all", Tr.now.homeAllGroups))
                        repo.subscriptions.forEach { add(IosMenuOption(it.id, it.name)) }
                        add(IosMenuOption("manual", Tr.now.homeManualGroup))
                    }
                    IosDropdownField(
                        label = trx("Pinned Subscription Group"),
                        value = s.dockSlotSourceId.ifBlank { "all" },
                        options = sourceOptions,
                        onSelect = { repo.updateSettings(s.copy(dockSlotSourceId = it)) }
                    )
                } else if (slotKind == DockSlotKind.CONFIG && repo.libraryProfiles.isNotEmpty()) {
                    val profileOptions = repo.libraryProfiles.map { p ->
                        IosMenuOption(
                            value = "${p.subscriptionId}|${p.id}",
                            label = cleanServerName(p.name),
                            detail = "${protocolShortLabel(p)} · ${p.subscriptionName}"
                        )
                    }
                    val currentKey = "${s.dockSlotProfileSourceId}|${s.dockSlotProfileId}"
                    IosDropdownField(
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

                IosCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        IosTextField(
                            label = trx("Custom Tab Label (optional)"),
                            value = s.dockSlotLabel,
                            onValueChange = { repo.updateSettings(s.copy(dockSlotLabel = it.take(DOCK_SLOT_CAPTION_MAX))) }
                        )
                        IosChipStrip {
                            DockSlotIcon.entries.forEach { ic ->
                                IosPillChip(
                                    label = ic.id.uppercase(),
                                    selected = slotIcon == ic,
                                    onClick = { repo.updateSettings(s.copy(dockSlotIcon = ic.id)) }
                                )
                            }
                        }
                        IosChipStrip {
                            DockSlotAccent.entries.forEach { ac ->
                                IosPillChip(
                                    label = ac.id.uppercase(),
                                    selected = slotAccent == ac,
                                    accent = dockSlotIosColor(ac),
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
    val colors = IosTheme.colors
    val s = repo.settings
    val connectStyle = parseConnectButtonStyle(s.connectButtonStyle)

    IosSubPageScaffold(
        title = Tr.now.homeStyleTitle,
        onBack = onBack
    ) {
        item(key = "home-connect-style") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Connect Control Style"))
                IosCard {
                    IosChoiceRow(
                        title = trx("iOS Button & Power Ring"),
                        subtitle = trx("Tap the circular status emblem or primary Connect button"),
                        selected = connectStyle != ConnectButtonStyle.SLIDE,
                        glyph = MarbleGlyph.POWER,
                        squircleColor = colors.green,
                        onClick = { repo.updateSettings(s.copy(connectButtonStyle = ConnectButtonStyle.ROUND.id)) }
                    )
                    IosDivider()
                    IosChoiceRow(
                        title = trx("iOS Slide to Connect"),
                        subtitle = trx("Swipe horizontally across the pill track to connect or disconnect"),
                        selected = connectStyle == ConnectButtonStyle.SLIDE,
                        glyph = MarbleGlyph.CHEVRON,
                        squircleColor = colors.blue,
                        onClick = { repo.updateSettings(s.copy(connectButtonStyle = ConnectButtonStyle.SLIDE.id)) }
                    )
                }
            }
        }

        item(key = "home-cards-visibility") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Visible Home Cards"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Network Speed & Session Data"),
                        subtitle = trx("Show live throughput and session/total usage card"),
                        checked = s.modularShowStats,
                        glyph = MarbleGlyph.GAUGE,
                        squircleColor = colors.blue,
                        onCheckedChange = { repo.updateSettings(s.copy(modularShowStats = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Local SOCKS5 Row"),
                        subtitle = trx("Always display the loopback SOCKS5 address on Home"),
                        checked = s.modularShowSocks,
                        glyph = MarbleGlyph.LINK,
                        squircleColor = colors.indigo,
                        onCheckedChange = { repo.updateSettings(s.copy(modularShowSocks = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Quick Actions Card"),
                        subtitle = trx("Show clipboard import, QR scanner, Smart Rank and Censorship Lab"),
                        checked = s.modularShowShortcuts,
                        glyph = MarbleGlyph.SPARK,
                        squircleColor = colors.orange,
                        onCheckedChange = { repo.updateSettings(s.copy(modularShowShortcuts = it)) }
                    )
                }
            }
        }
    }
}
