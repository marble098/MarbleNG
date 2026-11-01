package com.marbleng.app.model

/**
 * MARBLE_LUMEN_DOCK_V256 — the navigation dock's numbers, and the one setting that decides where
 * the connect control lives.
 *
 * Three things were reported about the bottom chrome and each of them is arithmetic rather than
 * taste, so each of them lives here where `DockStudioV256Test` can pin it on the host JVM:
 *
 *  1. **The pill clipped.** A floating bar is a `height + margin` object above the gesture bar, and
 *     the *scroll clearance* every page reserves has to be at least that. [fitsClearance] is the
 *     gate: a size that would not fit is a size the settings page must not offer, and the shell
 *     must never draw a capsule the last row of a list can hide under.
 *  2. **The bar had one height.** The setting offered Compact / Standard / Large of a fixed 68dp
 *     capsule. The ladder is now 48 / 56 / 64 dp of *capsule*, and padding, glyph and the expanded
 *     label are derived from it instead of being three constants that could drift apart.
 *  3. **A selected tab kept its caption forever.** The transparent label is now a **transient**:
 *     [EXPANDED_HOLD_MS] of icon + word, then the capsule relaxes back to the glyph, so four tabs
 *     fit on a small phone without a permanent second row of text.
 *
 * Nothing here is Android: the numbers, the ladder and the style enum are the contract, and the
 * composable that draws them reads this file rather than repeating it.
 */
object DockStudio {
    const val MARKER: String = "MARBLE_LUMEN_DOCK_V256"

    /** Air between the pill and the screen edge. The pill floats; it is never full-bleed. */
    const val HORIZONTAL_MARGIN_DP: Int = 20

    /** Air between the pill and the gesture bar — the 16dp the brief asks for, inside the inset. */
    const val BOTTOM_MARGIN_DP: Int = 16

    /** How long a newly selected tab shows its word before it relaxes back to its glyph. */
    const val EXPANDED_HOLD_MS: Long = 2_000L

    /**
     * How long the centre trigger's tooltip stays after a tap.
     *
     * Shorter than the tab caption on purpose: the caption confirms a navigation the reader has
     * just made, while the tooltip is a reading they already knew they wanted. Long enough to read
     * a latency, short enough that the card is never mistaken for part of the bar.
     */
    const val CENTER_TOOLTIP_HOLD_MS: Long = 1_400L

    /**
     * The spring under every dock movement.
     *
     * `DampingRatioMediumBouncy` + `StiffnessMediumLow` is the brief's own pair: the capsule
     * overshoots a little on arrival (which is what makes it read as an object rather than a
     * rectangle fading), and the low stiffness keeps the stretch legible instead of a snap.
     */
    const val SPRING_DAMPING_RATIO: Float = 0.6f
    const val SPRING_STIFFNESS: Float = 380f

    /** The circle a tab rests in before it becomes a capsule: half the dock height, no wider. */
    fun restingTabSizeDp(size: DockSize): Int = heightDp(size) - pillPaddingDp(size) * 2

    /** The capsule height of the ladder. Compact 48, Standard 56, Comfortable 64. */
    fun heightDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 48
        DockSize.MEDIUM -> 56
        DockSize.LARGE -> 64
    }

    /**
     * Inner air: 4 / 6 / 8 dp. The glyph never touches the rim, and the difference between two
     * rungs is what makes the ladder visible at a glance.
     */
    fun pillPaddingDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 4
        DockSize.MEDIUM -> 6
        DockSize.LARGE -> 8
    }

    /** 20 / 22 / 24 dp glyphs: the icon grows with the capsule, never past the row icon size. */
    fun iconSizeDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 20
        DockSize.MEDIUM -> 22
        DockSize.LARGE -> 24
    }

    /** A stadium, at every rung: the corner radius is exactly half the height. */
    fun cornerRadiusDp(size: DockSize): Float = heightDp(size) / 2f

    /**
     * Width of a tab while its word is showing: the resting circle plus the word's own room.
     * Derived from the height so a Comfortable dock's capsule is proportionally wider rather than
     * a Compact capsule with a long word squeezed into it.
     */
    fun expandedTabWidthDp(size: DockSize, labelChars: Int): Int {
        val base = restingTabSizeDp(size)
        val perChar = when (size) {
            DockSize.SMALL -> 7
            DockSize.MEDIUM -> 8
            DockSize.LARGE -> 9
        }
        return base + 20 + labelChars.coerceIn(1, 14) * perChar
    }

    /** The pill plus the air under it — what the bottom of the screen actually loses. */
    fun occupiedHeightDp(size: DockSize): Int = heightDp(size) + BOTTOM_MARGIN_DP

    /**
     * Does this rung fit the clearance every screen already reserves?
     *
     * `MarbleSpec.BOTTOM_BAR_CLEARANCE_DP` is 112dp and the screens spend it as scroll padding. A
     * dock taller than that would sit on the last row of every list — the clipping the brief
     * reported, from the other side of the same fact.
     */
    fun fitsClearance(size: DockSize, clearanceDp: Int = 112): Boolean =
        occupiedHeightDp(size) <= clearanceDp

    /** Every rung has to fit, or the settings page is offering a size the shell cannot honour. */
    fun ladderFits(clearanceDp: Int = 112): Boolean = DockSize.entries.all { fitsClearance(it, clearanceDp) }

    /** The settings labels, in ladder order, as the English keys `trx` translates. */
    fun ladderTitle(size: DockSize): String = when (size) {
        DockSize.SMALL -> "Compact"
        DockSize.MEDIUM -> "Standard"
        DockSize.LARGE -> "Comfortable"
    }

    fun ladderHeightLabel(size: DockSize): String = "${heightDp(size)} dp"

    /**
     * The right edge's power button: a circle as tall as the inner row, so it is the same tactile
     * object at every rung and never taller than the capsule it is embedded in.
     */
    fun powerButtonSizeDp(size: DockSize): Int = restingTabSizeDp(size)

    /** The island capsule's height and its two pill paddings — compact enough to leave tabs room. */
    fun islandHeightDp(size: DockSize): Int = (heightDp(size) - pillPaddingDp(size) * 2).coerceAtLeast(32)

    fun islandPaddingDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 10
        DockSize.MEDIUM -> 12
        DockSize.LARGE -> 14
    }

    /**
     * The island is a *badge*, not a banner: its width is bounded so four tabs always keep their
     * glyphs on a 360dp phone even while it shows a batch counter.
     */
    fun islandMaxWidthDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 96
        DockSize.MEDIUM -> 108
        DockSize.LARGE -> 120
    }

    /** "48 ms" — the inline latency the capsule carries after a tap. */
    fun pingLabel(latencyMs: Int): String = if (latencyMs <= 0) "" else "$latencyMs ms"

    /**
     * "12/79 pinging…" — the silent batch tracker. The word is the English key `trx` translates;
     * the numbers use the caller's digit shaper.
     */
    fun batchLabel(done: Int, total: Int): String {
        val safeTotal = total.coerceAtLeast(0)
        val safeDone = done.coerceIn(0, safeTotal)
        return "$safeDone/$safeTotal pinging…"
    }

    /** The micro bar under the counter, clamped so a shrinking batch cannot draw backwards. */
    fun batchProgress(done: Int, total: Int): Float =
        if (total <= 0) 0f else done.coerceIn(0, total).toFloat() / total.toFloat()

    /** True while a batch is running: the capsule is the only surface allowed to say so. */
    fun showsBatch(probeActive: Boolean): Boolean = probeActive
}

/**
 * MARBLE_LUMEN_DOCK_V256 — where the connect control lives.
 *
 * The three shapes are not skins of one control: each answers a different complaint, so the choice
 * is a real preference rather than a theme colour.
 *
 * | style | the complaint it answers |
 * |---|---|
 * | [DOCK_ISLAND] | "I never want to leave the page to disconnect." The pill grows a status badge on the left and a power button on the right. |
 * | [CENTER_MORPH] | "The dock is a toolbar, not a switchboard." One spring button in the middle blooms into the action, and its latency is a tooltip that leaves with the finger. |
 * | [HERO_CARD] | "Keep Home the way it was, just calmer." The large card stays, flattened and quiet. |
 *
 * The default is [DOCK_ISLAND] because it is the one that keeps the connect control reachable on
 * every tab, and because a power button inside the dock is the smallest number of taps the product
 * has ever offered for the action it exists for.
 */
enum class ConnectionWidgetStyle(val id: String, val title: String, val hint: String) {
    DOCK_ISLAND(
        id = "dock_island",
        title = "Dock island",
        hint = "Status capsule on the left, power button on the right, inside the navigation dock"
    ),
    CENTER_MORPH(
        id = "center_morph",
        title = "Center morph",
        hint = "One spring button in the middle of the dock, with its latency in a tooltip"
    ),
    HERO_CARD(
        id = "hero_card",
        title = "Hero card",
        hint = "The large connect card stays on Home, flattened and calm"
    );

    /** The style that keeps Home the shape it always had. */
    val showsHomeHero: Boolean get() = this == HERO_CARD

    /** The style that puts a power button in the dock's right edge. */
    val showsDockPower: Boolean get() = this == DOCK_ISLAND

    /** The style that puts a single morphing trigger in the dock's centre. */
    val showsCenterTrigger: Boolean get() = this == CENTER_MORPH

    companion object {
        val DEFAULT: ConnectionWidgetStyle = DOCK_ISLAND

        /** Unknown ids resolve to the default, so a restored backup never blanks the dock. */
        fun fromId(raw: String?): ConnectionWidgetStyle =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT

        fun labelOf(raw: String?): String = fromId(raw).title
    }
}

/**
 * MARBLE_HOME_WIDGETS_V256 — the four Home blocks the brief asked to be switchable on their own.
 *
 * [HomeStudio] already owned the *section* list; this is the finer grain inside two of those
 * sections, and the two are deliberately different questions:
 *
 *  - a section is "does Home draw this at all" — the layout the user arranges;
 *  - a widget is "does this card exist inside a section that stays" — the identity box under the
 *    route, the live graph under the preset strip.
 *
 * The title and hint strings are the English keys `trx` translates, spelled exactly as the Persian
 * dictionary spells them.
 */
enum class HomeWidget(val id: String, val title: String, val hint: String) {
    ROUTE_CARD(
        id = "route_card",
        title = "Active Route Card",
        hint = "The live server, its flag and its measured ping"
    ),
    PRESETS(
        id = "presets",
        title = "Tuned for You presets",
        hint = "Gaming, streaming and blocked-network one-tap modes"
    ),
    SPEED_GRAPH(
        id = "speed_graph",
        title = "Live speed graph",
        hint = "Download and upload throughput as they happen"
    ),
    IP_BOX(
        id = "ip_box",
        title = "Detailed IP & classification",
        hint = "The exit address, its family and how the internet sees it"
    );

    companion object {
        fun fromId(raw: String?): HomeWidget? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * MARBLE_HOME_WIDGETS_V256 — the resolution between the two layers.
 *
 * Written as one function because there are exactly two callers that must agree (the Home page and
 * the settings page's "n of 4" badge) and a second copy of these rules is how they stop agreeing.
 */
object HomeWidgets {
    /** Visible when the section is drawn *and* its own switch is on. */
    fun isVisible(widget: HomeWidget, showRouteCard: Boolean, showPresets: Boolean, showSpeedGraph: Boolean, showIpBox: Boolean): Boolean =
        when (widget) {
            HomeWidget.ROUTE_CARD -> showRouteCard
            HomeWidget.PRESETS -> showPresets
            HomeWidget.SPEED_GRAPH -> showSpeedGraph
            HomeWidget.IP_BOX -> showIpBox
        }

    fun visibleCount(showRouteCard: Boolean, showPresets: Boolean, showSpeedGraph: Boolean, showIpBox: Boolean): Int =
        listOf(showRouteCard, showPresets, showSpeedGraph, showIpBox).count { it }

    /** Every widget back on — what "Reset Home settings" writes. */
    const val ALL_ON: Boolean = true
}
