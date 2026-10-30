package com.marbleng.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.LiquidDockSpec

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
 * MARBLE_LIQUID_DOCK_V245 / MARBLE_LIQUID_GLASS_LENS_V247 — the adapter between the app's tab model
 * and the liquid-glass bar.
 *
 * V245 drew the magnified glyph itself: a Gaussian falloff read in `graphicsLayer`, one icon
 * growing under a drawn ellipse. V247 replaced that optic with a real projection —
 * [LiquidGlassBottomBar] renders the row twice and reveals the magnified copy through the lens
 * aperture — so this file is now only the translation layer, and it is deliberately thin:
 *
 * - the settings the app already promises still hold (dock size, floating versus attached, labels
 *   on or off, icons on or off, the fourth tab's own glyph and accent, the connection light);
 * - the glyph is handed over as a composable slot, which is what lets both optical layers draw the
 *   same vector art at scale 1.0 and at projection scale;
 * - the counter keeps the Persian digit shaper, so a Persian screen prints ۲ and not 2;
 * - the test tags of V245 are preserved, so the regression suite that inflates this bar keeps
 *   checking the same four tabs and the same badge.
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
    floating: Boolean = true,
    backdrop: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero
) {
    if (tabs.isEmpty()) return

    val glassTabs = tabs.map { tab ->
        GlassTabItem(
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
        tabs = glassTabs,
        selectedIndex = selectedIndex,
        onTabSelected = onSelect,
        modifier = modifier,
        barHeight = LiquidDockSpec.barHeightDp(size).dp,
        floating = floating,
        showIcons = showIcons,
        showLabels = showLabels,
        iconSize = LiquidDockSpec.iconSizeDp(size).dp,
        badgeLabel = { count -> faDigits(LiquidDockSpec.badgeLabel(count)) },
        backdrop = backdrop,
        backdropOrigin = backdropOrigin,
        tagPrefix = "dock-tab-",
        badgeTagPrefix = "dock-badge-"
    )
}
