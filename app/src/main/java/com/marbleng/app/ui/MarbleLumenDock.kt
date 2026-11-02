package com.marbleng.app.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import com.marbleng.app.model.LiquidLens
import com.marbleng.app.ui.theme.marbleMotion

/**
 * MARBLE_LUMEN_DOCK_V256 — the floating navigation dock, rebuilt against the four complaints a
 * screenshot cannot argue with.
 *
 * **1. It clipped.** The bar was a `height`-tall object laid out from the bottom edge, so a
 * three-button phone, a gesture phone and a phone with a notch each put the capsule at a different
 * height — and on the worst of them its lower third sat under the system bar. This container is
 * inset *before* it is measured:
 *
 * ```
 * .windowInsetsPadding(WindowInsets.navigationBars)                      // the system bar itself
 * .windowInsetsPadding(WindowInsets.safeDrawing.only(Horizontal + Top))  // cutouts, hinges, RTL edges
 * ```
 *
 * and then [DockStudio.BOTTOM_MARGIN_DP] of its own air. Two passes rather than
 * `safeDrawingPadding()` *and* `navigationBars`: those two together are the same bug from the other
 * side — the bottom would be paid for twice and the pill would float a navigation-bar too high on
 * every gesture phone.
 *
 * **2. It had one size.** The ladder is 56 / 68 / 82 dp ([DockStudio.heightDp]), and the padding,
 * the glyph and the lens are derived from the rung instead of restated per call site.
 *
 * **3. A selected tab opened its caption.** MARBLE_QUIET_TABS_V263 removed it: no transient word,
 * no timer, no spring-animated slot weight and no permanent word on the largest rung. Every slot
 * keeps a fixed `weight(1f)`, so a tap changes the page and the lens travels over a row whose
 * geometry never moves. The tab's word is still read by a screen reader: [NavigationItemView] keeps
 * it on the slot's `contentDescription` instead of drawing it.
 *
 * **4. The motion was a tween.** Every movement here is a spring — the magnification of the glyph,
 * the tint of the ink, the travel of the glass. V258 shipped
 * `spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)`; V262 moved the pair into
 * [LiquidLens.SPRING_DAMPING_RATIO] / [LiquidLens.SPRING_STIFFNESS] (0.75 / 400 — the brief's
 * number rather than the medium-bouncy token it names, which is 0.5; the reasoning lives on the
 * constant and is pinned by `LiquidLensV262Test`). The app's
 * calm-motion switch and Android's own *remove animations* still win — [marbleMotion] clamps the
 * travel and a still motion settles without overshoot.
 *
 * ## MARBLE_CENTER_MORPH_STUDIO_V259 — the centre trigger is not a fifth tab
 *
 * When [centerProud] is set the whole capsule grows by [DockStudio.CENTER_PROUD_DP], the tabs sit in
 * the row as before, and the trigger fills the taller row: a solid tonal disc with a specular rim
 * and a stack of three elevation shadows. It is the only object in the product that is deliberately
 * bigger than the chrome it lives in.
 *
 * ## MARBLE_LIQUID_LENS_V262 — the flat aperture became a lens
 *
 * V261 travelled an accent wash under the row. This pass replaces it with the object that wash was
 * standing in for, and it splits the dock into the three files the architecture asks for:
 *
 * | file | owns |
 * |---|---|
 * | `LiquidLensNavigationBar.kt` | the pill: material, lift, measurement, the drag, the lens's placement |
 * | `GlassLensIndicator.kt` | the lens: blur, refraction, specular rim, the spring and its deformation |
 * | `NavigationItemView.kt` | one tab: magnification, ink, counter, connection light |
 *
 * This file is still the adapter, and it is still deliberately thin. Everything the settings
 * promise holds — dock size, the chosen air under the capsule, the icon-only tabs, the fourth tab's
 * own glyph and accent, the connection light, the red counter, the three connection-widget styles, and the
 * five slots in their original order (Home, Servers, the centre action, IP, Settings) — and it is
 * translated into one [MarbleDockTab] per visible tab plus the two wing slots. No route, no state,
 * no view model and no badge count is touched by the material: the dock draws what it is given and
 * reports the index that was chosen.
 */
@Composable
internal fun MarbleLumenDock(
    tabs: List<MarbleDockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: DockSize = DockSize.MEDIUM,
    /**
     * MARBLE_FLOATING_DOCK_V258 — the air under the capsule, in dp, chosen by the user. It is a
     * parameter rather than a constant because it is a *setting*: the dock floats, and how far
     * above the gesture bar is the reader's answer, not this file's.
     */
    bottomGap: Dp = DockStudio.DEFAULT_BOTTOM_GAP_DP.dp,
    /**
     * MARBLE_CENTER_MORPH_STUDIO_V259 — true while a centre trigger is going to be drawn.
     *
     * The capsule grows by [DockStudio.CENTER_PROUD_DP] and the rung's own material gives way to the
     * Expressive one. It is a parameter rather than `center != null` only because the dock is also
     * drawn in tests and previews without a wing, and those must stay at the shipped height.
     */
    centerProud: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    center: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    if (tabs.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val height = if (centerProud && center != null) {
        DockStudio.centerDockHeightDp(size)
    } else {
        DockStudio.heightDp(size)
    }
    val pillPadding = DockStudio.pillPaddingDp(size)
    val selection = selectedIndex.coerceIn(0, tabs.lastIndex)
    val tabCount = tabs.size
    val haptics = LocalHapticFeedback.current

    // The brief's pair — 0.75 / StiffnessMediumLow — with calm motion and the platform's
    // remove-animations honoured on top: a still app does not spring, it arrives.
    val floatSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = if (motion.still) Spring.DampingRatioNoBouncy else LiquidLens.SPRING_DAMPING_RATIO,
        stiffness = if (motion.still) Spring.StiffnessHigh else LiquidLens.SPRING_STIFFNESS
    )
    val colorSpec: FiniteAnimationSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (motion.still) Spring.StiffnessHigh else Spring.StiffnessMediumLow
    )

    // MARBLE_FLOATING_DOCK_V258 — one presentation, always floating. The `attached` branch that
    // laid a full-bleed strip on the gesture bar was the oldest-looking surface in the product and
    // it is gone: the capsule floats, and only its distance from the bottom is configurable.
    val container = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        )
        .padding(
            start = DockStudio.HORIZONTAL_MARGIN_DP.dp,
            end = DockStudio.HORIZONTAL_MARGIN_DP.dp,
            bottom = bottomGap
        )

    Box(
        modifier = modifier
            .then(container)
            .testTag("lumen-dock"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = DockStudio.MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .height(height.dp)
        ) {
            LiquidLensNavigationBar(
                selectedIndex = selection,
                slotCount = tabCount,
                height = height.dp,
                // `RoundedCornerShape(percent = 50)`: half the *drawn* height, which is exactly
                // `DockStudio.cornerRadiusDp(size)` on every rung (LiquidLensV262Test pins the two
                // equal) and one step rounder on the taller centre capsule — the object grew, so
                // its corner grew with it instead of the stadium turning into a rounded rectangle.
                corner = (height / 2f).dp,
                trackInset = pillPadding.dp,
                surface = colors.surfaceContainerHigh,
                highlight = colors.primary,
                second = colors.tertiary,
                // The material is a glass, so it has to know which way the light comes from: on a
                // dark page the tonal ground is lifted and the white pool is reduced, because 0.55
                // white over black is a lamp and not a pane.
                dark = colors.surface.luminance() < 0.5f,
                accent = tabs.getOrNull(selection)?.accent ?: colors.primary,
                reduced = motion.still,
                modifier = Modifier.fillMaxSize(),
                // A drag moves the selection to the slot under the finger: a lens that travels on
                // tap alone is a highlight with better manners. The container has already decided
                // *which* slot that is; the haptic and the callback belong to the app.
                onDragSelect = { index ->
                    if (index in 0 until tabCount && index != selection) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    }
                }
            ) { slotHost ->
                if (leading != null) leading()

                // A centred trigger splits the tabs around itself: half of them lead, half follow,
                // so the button is the middle of the dock and not a fifth slot at one end. With the
                // shipped four tabs the row reads Home · Servers · (action) · IP · Settings.
                val centerAt = if (center != null) (tabCount + 1) / 2 else -1
                tabs.forEachIndexed { index, tab ->
                    if (index == centerAt) {
                        center?.let { trigger ->
                            Box(
                                modifier = Modifier.padding(horizontal = pillPadding.dp),
                                contentAlignment = Alignment.Center
                            ) { trigger() }
                        }
                    }
                    // MARBLE_QUIET_TABS_V263 — no slot ever changes its weight: the row is measured
                    // once and the lens always sits over a tab whose geometry did not move on a tap.
                    key(tab.key) {
                        NavigationItemView(
                            tab = tab,
                            selected = index == selection,
                            size = size,
                            spatialSpec = floatSpec,
                            colorSpec = colorSpec,
                            onClick = { onSelect(index) },
                            // The slot's own rectangle, measured rather than assumed: this is what
                            // keeps the lens over its tab through insets, wings and a centre trigger.
                            onPlaced = { bounds -> slotHost.reportSlot(index, bounds) },
                            testTag = "lumen-tab-${tab.key}",
                            badgeTag = "lumen-tab-count-${tab.key}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (centerAt == tabCount) {
                    center?.let { trigger ->
                        Box(
                            modifier = Modifier.padding(horizontal = pillPadding.dp),
                            contentAlignment = Alignment.Center
                        ) { trigger() }
                    }
                }

                if (trailing != null) trailing()
            }
        }
    }
}
