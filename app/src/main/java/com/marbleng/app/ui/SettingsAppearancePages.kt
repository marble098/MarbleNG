package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.marbleng.app.model.MarblePalette
import com.marbleng.app.model.parseMarblePalette
import com.marbleng.app.ui.theme.marblePaletteSignature
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import com.marbleng.app.model.ConnectionWidgetStyle
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import com.marbleng.app.model.HomeWidget
import com.marbleng.app.model.HomeWidgets
import com.marbleng.app.model.DockSlotAccent
import com.marbleng.app.model.DockSlotKind
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.model.HomeSection
import com.marbleng.app.model.HomeQuiet
import com.marbleng.app.model.HomeStudio
import com.marbleng.app.model.TextScale
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseAppLanguage
import com.marbleng.app.model.encodeDockPreference
import com.marbleng.app.model.parseDockSize
import com.marbleng.app.model.parseDockSlotAccent
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

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.PALETTE,
        tint = colors.primary,
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
        // MARBLE_PALETTE_STUDIO_V258 — whole-scheme colour themes, above the accent swatches
        // because they outrank them: a palette replaces primary, secondary, tertiary, the container
        // tones and the surface ladder at once, while an accent only seeds primary.
        item(key = "palette-studio") {
            val activePalette = parseMarblePalette(s.themePalette)
            val paletteDark = resolveMarbleDarkMode(s.theme, isSystemInDarkTheme())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Colour themes"),
                    badge = trx(activePalette.label)
                )
                MarbleCard {
                    MarblePalette.entries.forEachIndexed { index, palette ->
                        if (index > 0) MarbleDivider()
                        val chosen = palette == activePalette
                        val signature = marblePaletteSignature(palette, paletteDark)
                        val paletteDescription = trx(palette.label)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = paletteDescription }
                                .clickable(role = Role.Button) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    repo.updateSettings(
                                        s.copy(
                                            appBehavior = s.appBehavior.copy(themePalette = palette.id)
                                        )
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("palette-${palette.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(signature))
                                    .then(
                                        if (chosen) {
                                            Modifier.border(2.dp, colors.onSurface, RoundedCornerShape(16.dp))
                                        } else {
                                            Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (chosen) {
                                    MarbleGlyphIcon(
                                        glyph = MarbleGlyph.CHECK,
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        weight = 1.25f
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = trx(palette.label),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = trx(palette.hint),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                MarbleSectionFooter(
                    text = trx(
                        "A palette repaints every surface, card and control in the app. It outranks the accent swatch and the wallpaper palette; Marble Classic hands both back."
                    )
                )
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

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.FONT,
        tint = colors.secondary,
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
                                    label = faDigits("$preset%"),
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
    val gap = DockStudio.bottomGapDp(s.dockBottomGapDp)
    val slotAccent = parseDockSlotAccent(s.dockSlotAccent)
    // Resolved in the composition: an onClick lambda is not a composable context.
    val openIpPageNotice = trx("Tap the fourth tab to open the IP page")

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.DOCK,
        tint = colors.tertiary,
        title = trx("Bottom bar & fourth tab"),
        subtitle = trx("One floating capsule: its size, its distance from the bottom, and the IP tab"),
        onBack = onBack
    ) {
        // MARBLE_FLOATING_DOCK_V258 — the page is rebuilt around the two decisions that survive.
        //
        // The old page offered four controls and three of them were chores: attached-versus-floating
        // (the attached presentation is retired), show-icons (a dock without icons is a row of
        // unlabelled dots) and show-labels (which the rung now decides, because text inside a 56dp
        // capsule is a different design, not a toggle). What is left is the size ladder — now three
        // visibly different capsules — and how far the capsule floats above the gesture bar.
        item(key = "dock-ladder") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(
                    title = trx("Capsule size"),
                    badge = trx(DockStudio.ladderTitle(dockSize))
                )
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        MarbleSegmentedControl(
                            options = DockSize.entries.map { it to trx(DockStudio.ladderTitle(it)) },
                            selected = dockSize,
                            onSelect = { picked ->
                                repo.updateSettings(
                                    s.copy(
                                        dockSize = encodeDockPreference(picked)
                                    )
                                )
                            }
                        )
                        // Each rung states what actually changes — height, glyph and caption
                        // behaviour — instead of a bare number the reader has to imagine.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DockSize.entries.forEach { rung ->
                                val active = rung == dockSize
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (active) {
                                                colors.primary.copy(alpha = 0.12f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable(role = Role.Button) {
                                            repo.updateSettings(
                                                s.copy(
                                                    dockSize = encodeDockPreference(rung)
                                                )
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 34.dp, height = DockStudio.heightDp(rung).dp / 2)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(
                                                if (active) colors.primary else colors.surfaceContainerHighest
                                            )
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = trx(DockStudio.ladderTitle(rung)),
                                            color = if (active) colors.primary else colors.onSurface,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = trx(DockStudio.ladderHint(rung)),
                                            color = colors.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    MarbleBadge(
                                        text = faDigits(DockStudio.ladderHeightLabel(rung)),
                                        color = if (active) colors.primary else colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                MarbleSectionFooter(
                    text = trx(
                        "Slim keeps glyphs only, Classic opens the selected word for a moment, Grand keeps it open."
                    )
                )
            }
        }

        item(key = "dock-gap") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(
                    title = trx("Distance from the bottom"),
                    badge = faDigits("$gap dp")
                )
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Live preview: the capsule at the chosen rung, floating at the chosen gap,
                        // so the slider is a measurement the reader can see instead of a number.
                        DockFloatPreview(
                            insetDp = DockStudio.HORIZONTAL_MARGIN_DP,
                            heightDp = DockStudio.heightDp(dockSize),
                            gapDp = gap,
                            glyph = MarbleGlyph.DOCK,
                            tint = colors.tertiary,
                            gapLabel = faDigits("$gap dp")
                        )
                        Slider(
                            value = gap.toFloat(),
                            onValueChange = { raw ->
                                repo.updateSettings(
                                    s.copy(dockBottomGapDp = DockStudio.bottomGapDp(raw.roundToInt())),
                                    coalesceWrite = true
                                )
                            },
                            valueRange = DockStudio.MIN_BOTTOM_GAP_DP.toFloat()..
                                DockStudio.MAX_BOTTOM_GAP_DP.toFloat(),
                            steps = (
                                (DockStudio.MAX_BOTTOM_GAP_DP - DockStudio.MIN_BOTTOM_GAP_DP) /
                                    DockStudio.BOTTOM_GAP_STEP_DP
                                ) - 1,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.tertiary,
                                activeTrackColor = colors.tertiary,
                                inactiveTrackColor = colors.outlineVariant
                            )
                        )
                        MarbleChipStrip {
                            listOf(
                                DockStudio.MIN_BOTTOM_GAP_DP,
                                DockStudio.DEFAULT_BOTTOM_GAP_DP,
                                DockStudio.MAX_BOTTOM_GAP_DP
                            ).forEach { preset ->
                                MarblePillChip(
                                    label = faDigits("$preset dp"),
                                    selected = gap == preset,
                                    accent = colors.tertiary,
                                    onClick = { repo.updateSettings(s.copy(dockBottomGapDp = preset)) }
                                )
                            }
                        }
                    }
                }
                MarbleSectionFooter(
                    text = trx(
                        "Pages reserve scroll room from this value, so raising it never hides the last row behind the capsule."
                    )
                )
            }
        }

        item(key = "fourth-tab-config") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Fourth Tab"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Show Fourth Tab"),
                        subtitle = trx("The IP page: every address this phone holds, in every state"),
                        checked = s.dockSlotEnabled,
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(dockSlotEnabled = it)) }
                    )
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

                // MARBLE_IP_IDENTITY_V251 — the tab stopped being a shortcut, so three settings left
                // with it: which kind of shortcut, which group or server it pinned, and the custom
                // caption. What is left is appearance — the glyph and the accent — plus one page
                // that explains what the tab now is, instead of a picker for something it no
                // longer does.
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = trx(
                                "The fourth tab is the IP page now. It opens the same way whether the " +
                                    "tunnel is up or down, and it draws every address this phone holds, " +
                                    "the address the internet sees, and which of IPv4 and IPv6 answers."
                            ),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
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
                        MarbleButton(
                            label = trx("Open the IP page"),
                            glyph = MarbleGlyph.GLOBE,
                            style = MarbleButtonStyle.TINTED,
                            onClick = {
                                repo.updateSettings(s.copy(dockSlotKind = DockSlotKind.IP.id))
                                repo.setRuntimeMessage(openIpPageNotice)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * MARBLE_FLOATING_DOCK_V258 — the preview inside the "distance from the bottom" card.
 *
 * It is drawn from the same numbers the real dock reads ([DockStudio]), at a third of the height,
 * so the slider is a measurement the reader can see: raise the value and the capsule lifts inside
 * this box by exactly the amount the real one will.
 */
@Composable
private fun DockFloatPreview(
    insetDp: Int,
    heightDp: Int,
    gapDp: Int,
    glyph: MarbleGlyph,
    tint: Color,
    gapLabel: String
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surfaceContainerHigh,
                        colors.surfaceContainerLow
                    )
                )
            )
            .padding(horizontal = insetDp.dp)
            .testTag("dock-gap-preview"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = gapDp.dp)
                .fillMaxWidth()
                .height(heightDp.dp / 2)
                .shadow(8.dp, shape)
                .clip(shape)
                .background(colors.surfaceContainerHighest)
                .background(
                    Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0.16f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MarbleGlyphIcon(glyph = glyph, color = tint, modifier = Modifier.size(16.dp))
                Text(
                    text = gapLabel,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/**
 * MARBLE_HOME_QUIET_V259 — the glyph a section wears in the studio.
 *
 * One mapping, next to the page that draws it: a section is not allowed to look like one thing in
 * Settings and another on Home, and the studio's rows are the only place the mapping is needed.
 */
private fun sectionGlyph(section: HomeSection): MarbleGlyph = when (section) {
    HomeSection.HEADER -> MarbleGlyph.HOME
    HomeSection.CONNECT -> MarbleGlyph.POWER
    HomeSection.PRESETS -> MarbleGlyph.SPARK
    HomeSection.ROUTE -> MarbleGlyph.ROUTE
    HomeSection.STATS -> MarbleGlyph.GAUGE
    HomeSection.SHORTCUTS -> MarbleGlyph.PLUS
}

@Composable
fun SettingsHomeLayoutPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val style = s.homeLayout.widgetStyle

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.HOME,
        tint = colors.warning,
        title = Tr.now.homeStyleTitle,
        subtitle = trx("Where the connect control lives on Home"),
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
                        text = trx(
                            "One pill connects and disconnects. The chip under it picks Smart, Full TUN or Local proxy. " +
                                "The slide track was removed: a swipe that had to land in the right half was a second " +
                                "thing to learn for one action."
                        ),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // MARBLE_HOME_QUIET_V259 — the page Home needed back: one switch per section.
        //
        // V258 removed the studio because a list of six rows whose only feedback was a page
        // rebuilding behind an animation was a worse Home than a designed one. The right answer is
        // neither extreme: Home *ships* quiet (heading, the one decision, its route), and this page
        // is where the rest is added, one named block at a time, with a live preview of the result
        // directly under the title. Three differences from the retired studio:
        //
        //  - Order is no longer something the reader drags. The six sections have one designed
        //    order and the page draws them in it; what the reader owns is *presence*.
        //  - Every switch states what it turns on, in the same words Home uses.
        //  - The preview is drawn from the real rules (`HomeStudio.isHidden`), not from a second
        //    opinion about them, so the strip cannot disagree with the page it describes.
        item(key = "home-section-switches") {
            val layout = s.homeLayout
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Sections on Home"),
                    badge = homeQuietShownLabel(
                        shown = HomeStudio.visible(
                            orderRaw = layout.sectionOrder,
                            hiddenRaw = layout.hiddenSections,
                            legacyStatsVisible = s.modularShowStats,
                            legacyShortcutsVisible = s.modularShowShortcuts
                        ).size
                    )
                )
                MarbleCard(contentPadding = PaddingValues(bottom = 6.dp)) {
                    MarbleHomeSectionPreview(
                        order = HomeStudio.order(layout.sectionOrder),
                        hiddenRaw = layout.hiddenSections,
                        legacyStatsVisible = s.modularShowStats,
                        legacyShortcutsVisible = s.modularShowShortcuts,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    MarbleSectionFooter(
                        text = trx("Home starts quiet: the heading, the connect control and the route it will use."),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    HomeSection.entries.forEach { section ->
                        val visible = !HomeStudio.isHidden(
                            section = section,
                            hiddenRaw = layout.hiddenSections,
                            legacyStatsVisible = s.modularShowStats,
                            legacyShortcutsVisible = s.modularShowShortcuts
                        )
                        // The heading is the page's name; hiding it would leave a feed with no title,
                        // so it is the one row that is always on and says why.
                        val locked = section == HomeSection.HEADER
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !locked, role = Role.Switch) {
                                    if (!locked) {
                                        val toggle = HomeQuiet.toggle(
                                            orderRaw = layout.sectionOrder,
                                            hiddenRaw = layout.hiddenSections,
                                            section = section,
                                            legacyStatsVisible = s.modularShowStats,
                                            legacyShortcutsVisible = s.modularShowShortcuts
                                        )
                                        repo.updateSettings(
                                            s.copy(
                                                modularShowStats = toggle.legacyStatsVisible,
                                                modularShowShortcuts = toggle.legacyShortcutsVisible,
                                                homeLayout = layout.copy(
                                                    sectionOrder = toggle.order,
                                                    hiddenSections = toggle.hidden
                                                )
                                            )
                                        )
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("home-section-${section.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(marbleExpressiveShape(MarbleExpressiveMotif.forGlyph(sectionGlyph(section))))
                                    .background(
                                        if (visible) {
                                            colors.primary.copy(alpha = 0.16f)
                                        } else {
                                            colors.surfaceContainerHighest
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                MarbleGlyphIcon(
                                    glyph = sectionGlyph(section),
                                    color = if (visible) colors.primary else colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                    weight = 1.12f
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = trx(section.title),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = trx(section.hint),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = visible,
                                onCheckedChange = { on ->
                                    if (!locked) {
                                        val toggle = HomeQuiet.toggle(
                                            orderRaw = layout.sectionOrder,
                                            hiddenRaw = layout.hiddenSections,
                                            section = section,
                                            legacyStatsVisible = s.modularShowStats,
                                            legacyShortcutsVisible = s.modularShowShortcuts
                                        )
                                        if (toggle.nowVisible != on) {
                                            repo.updateSettings(
                                                s.copy(
                                                    modularShowStats = toggle.legacyStatsVisible,
                                                    modularShowShortcuts = toggle.legacyShortcutsVisible,
                                                    homeLayout = layout.copy(
                                                        sectionOrder = toggle.order,
                                                        hiddenSections = toggle.hidden
                                                    )
                                                )
                                            )
                                        }
                                    }
                                },
                                enabled = !locked,
                                modifier = Modifier.testTag("home-section-switch-${section.id}")
                            )
                        }
                    }
                }
                MarbleSectionFooter(
                    text = trx(
                        "The order is designed, not dragged: the heading, the decision, the modes, " +
                            "the route, the live speed and the shortcuts."
                    )
                )
            }
        }

        // MARBLE_FLOATING_DOCK_V258 — the section studio and the widget switches are gone.
        //
        // They let a reader reorder Home's blocks, hide them, or delete them one at a time, and the
        // page became a second, worse Home editor: a list of six rows whose only feedback was a
        // page rebuilt behind an animation. Home is now a page with a fixed, designed order, and the
        // one decision this page keeps is where the connect control lives — a placement, not a
        // deletion.
        item(key = "home-widget-settings") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Connection widget"))
                MarbleCard(contentPadding = PaddingValues(bottom = 8.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = trx(style.title),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = trx(style.hint),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MarbleDropdownField(
                        label = trx("Connection style"),
                        value = style.id,
                        options = ConnectionWidgetStyle.entries.map { option ->
                            MarbleMenuOption(option.id, trx(option.title), trx(option.hint))
                        },
                        onSelect = { picked ->
                            repo.updateSettings(
                                s.copy(
                                    homeLayout = s.homeLayout.copy(
                                        connectionStyle = ConnectionWidgetStyle.fromId(picked).id
                                    )
                                )
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                MarbleSectionFooter(
                    text = trx("Home always draws every block in the order it was designed in.")
                )
            }
        }
    }
}
