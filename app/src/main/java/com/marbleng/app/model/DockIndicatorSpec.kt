package com.marbleng.app.model

import kotlin.math.ceil

/**
 * MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — the active-tab line has a geometry now.
 *
 * The old indicator was an unmeasured child of the icon Box, pushed down with
 * `offset(y = 10.dp)`: the Column never reserved space for it, so it landed on top of the label,
 * was clipped by the active pill and drifted with every dock size. These tokens are the single
 * source of truth for both the Composable and a host-JVM test: the line is a measured slot that
 * every tab reserves, whether it is selected or not, so labels never jump when the selection
 * changes.
 */
object DockIndicatorSpec {
    const val MARKER: String = "MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233"

    /** A short, low, rounded line — deliberately a line, not a second pill. */
    const val WIDTH_DP: Int = 18
    const val HEIGHT_DP: Int = 3

    /** Reserved in every tab. HEIGHT_DP plus at least 1dp of air above and below. */
    const val SLOT_DP: Int = 6

    /** Vertical padding inside the tab pill. Reduced from 6dp to pay for the measured slot. */
    const val TAB_VERTICAL_PADDING_DP: Int = 4

    /** `labelSmall` line height at 100 % text scale. */
    const val LABEL_LINE_HEIGHT_SP: Int = 15

    /** 56dp (was 52) gives the compact dock room for the line even at 140 % text scale. */
    fun barHeightDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 56
        DockSize.MEDIUM -> 60
        DockSize.LARGE -> 68
    }

    fun iconSizeDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 20
        DockSize.MEDIUM -> 23
        DockSize.LARGE -> 26
    }

    fun labelLineHeightDp(textScalePercent: Int = TextScale.DEFAULT_PERCENT): Int =
        ceil(LABEL_LINE_HEIGHT_SP * TextScale.factor(TextScale.clamp(textScalePercent))).toInt()

    fun contentHeightDp(
        size: DockSize,
        showIcons: Boolean,
        showLabels: Boolean,
        textScalePercent: Int = TextScale.DEFAULT_PERCENT
    ): Int =
        (if (showIcons) iconSizeDp(size) else 0) +
            SLOT_DP +
            (if (showLabels) labelLineHeightDp(textScalePercent) else 0) +
            TAB_VERTICAL_PADDING_DP * 2

    fun fits(
        size: DockSize,
        showIcons: Boolean,
        showLabels: Boolean,
        textScalePercent: Int = TextScale.DEFAULT_PERCENT
    ): Boolean = contentHeightDp(size, showIcons, showLabels, textScalePercent) <= barHeightDp(size)

    /** Every size, every icon/label combination, every rung of the text-scale ladder. */
    fun allFit(): Boolean = DockSize.entries.all { size ->
        TextScale.STEPS.all { scale ->
            listOf(true, false).all { icons ->
                listOf(true, false).all { labels -> fits(size, icons, labels, scale) }
            }
        }
    }
}
