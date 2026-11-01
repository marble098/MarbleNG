package com.marbleng.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.LiquidDockSpec
import com.marbleng.app.model.LiquidGlassLensSpec

/**
 * MARBLE_LIQUID_DOCK_V245 — one tab of the MarbleNG navigation pill, as the screens know it.
 *
 * [badgeCount] is the red counter (0 draws nothing). [statusDot] is the living connection light the
 * fourth slot carries when the tunnel is up. [accent] is the colour the tab becomes while the lens
 * covers it — electric blue for the three product tabs, the user's chosen slot accent for the
 * fourth.
 */
@Immutable
internal data class MarbleDockTab(
    val key: String,
    val label: String,
    val glyph: MarbleGlyph,
    val accent: Color,
    val badgeCount: Int = 0,
    val statusDot: Color? = null
)

/**
 * MARBLE_LIQUID_DOCK_V245 / MARBLE_CRYSTAL_LENS_V248 — the adapter between the app's tab model and
 * the crystal-lens bar.
 *
 * V245 drew the magnified glyph itself; V247 moved the optic to a two-pass projection; V248 rebuilt
 * that projection so the passive row is clipped out of the aperture instead of showing through it.
 * This file is only the translation layer, and it is deliberately thin:
 *
 * - the settings the app already promises still hold (dock size, floating versus attached, labels
 *   on or off, icons on or off, the fourth tab's own glyph and accent, the connection light);
 * - the glyph is handed over as a composable slot, which is what lets both optical passes draw the
 *   same vector art at scale 1.0 and at 1.30×;
 * - the counter keeps the Persian digit shaper, so a Persian screen prints ۲ and not 2;
 * - the test tags of V245 are preserved, so the regression suite that inflates this bar keeps
 *   checking the same four tabs and the same badge.
 *
 * The dock-size preference selects one of the two capsule heights the crystal-lens brief sanctions:
 * Compact and Standard are the 68dp capsule, Large is the 72dp one. The 52dp lens and its 8–10dp of
 * breathing room are therefore identical at every setting.
 */
@Composable
internal fun MarbleLiquidDock(
    tabs: List<MarbleDockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: DockSize = DockSize.MEDIUM,
    showIcons: Boolean = true,
    showLabels: Boolean = true,
    floating: Boolean = true
) {
    if (tabs.isEmpty()) return

    val items = tabs.map { tab ->
        NavigationTabItem(
            key = tab.key,
            title = tab.label,
            badgeCount = tab.badgeCount,
            accent = tab.accent,
            statusDot = tab.statusDot,
            icon = { tint, weight, iconModifier ->
                Canvas(modifier = iconModifier) {
                    drawMarbleGlyph(glyph = tab.glyph, tint = tint, weight = weight)
                }
            }
        )
    }

    LiquidGlassBottomBar(
        items = items,
        selectedIndex = selectedIndex,
        onTabSelected = onSelect,
        modifier = modifier,
        barHeight = LiquidGlassLensSpec.barHeightDp(size).dp,
        horizontalMargin = if (floating) LiquidGlassLensSpec.HORIZONTAL_MARGIN_DP.dp else 0.dp,
        bottomMargin = if (floating) 16.dp else 0.dp,
        showIcons = showIcons,
        showLabels = showLabels,
        badgeLabel = { count -> faDigits(LiquidDockSpec.badgeLabel(count)) },
        tagPrefix = "dock-tab-",
        badgeTagPrefix = "dock-badge-"
    )
}
