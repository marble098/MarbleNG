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
 *     capsule. The ladder is now 56 / 68 / 82 dp of *capsule* (V258: V256's 48 / 56 / 64 was
 *     inside the noise of a screenshot), and padding, glyph, label rung and caption behaviour are
 *     derived from it instead of being constants that could drift apart.
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
    const val HORIZONTAL_MARGIN_DP: Int = 18

    /** The rung the dock ships on. Larger than V256's 16dp: the capsule now visibly floats. */
    const val DEFAULT_BOTTOM_GAP_DP: Int = 22

    /**
     * MARBLE_FLOATING_DOCK_V258 — the old name for that default.
     *
     * Two generations of call sites, one pinned invariant and the dock's own KDoc name it, so the
     * alias stays; it is the *default* of the user's adjustable gap now, not a constant the dock
     * obeys.
     */
    const val BOTTOM_MARGIN_DP: Int = DEFAULT_BOTTOM_GAP_DP

    /** The adjustable range of [DEFAULT_BOTTOM_GAP_DP] — the setting's own bounds. */
    const val MIN_BOTTOM_GAP_DP: Int = 8
    const val MAX_BOTTOM_GAP_DP: Int = 44
    const val BOTTOM_GAP_STEP_DP: Int = 2

    /** The scroll clearance every screen reserved before the gap became adjustable. */
    const val BASE_CLEARANCE_DP: Int = 112

    /** Air the dock keeps between its own top rim and the last row of a list. */
    const val CLEARANCE_BREATHING_DP: Int = 26

    /** How long a newly selected tab shows its word before it relaxes back to its glyph. */
    const val EXPANDED_HOLD_MS: Long = 2_000L

    // ── MARBLE_CENTER_MORPH_STUDIO_V259 ─────────────────────────────────────────────────────────
    //
    // The centre trigger used to be a circle of exactly `restingTabSizeDp` — the same circle as a
    // tab — so the third connection style drew a fifth tab in the middle of the bar. The brief asks
    // for the opposite: at the centre it is a *different* object, slightly larger than everything
    // beside it, with Google's newer Expressive material. The numbers below are that object, and
    // they are here rather than in the composable so `DockStudioCenterMorphV259Test` can pin them.

    /**
     * How much taller the centre dock is than the same dock in another style.
     *
     * The trigger is not a fifth tab: the whole bar grows by 8dp while a centre trigger is drawn,
     * and the trigger fills that taller row. Two things follow from one constant — the capsule the
     * reader sees is bigger, and the circle inside it is bigger than the tab circles beside it.
     */
    const val CENTER_PROUD_DP: Int = 8

    /** The dock capsule while a centre trigger is drawn: the rung's height plus [CENTER_PROUD_DP]. */
    fun centerDockHeightDp(size: DockSize): Int = heightDp(size) + CENTER_PROUD_DP

    /**
     * The centre trigger at rest: the inner row of the taller capsule, which is exactly
     * [CENTER_PROUD_DP] wider than the resting circle of a tab at the same rung.
     */
    fun centerTriggerSizeDp(size: DockSize): Int =
        centerDockHeightDp(size) - pillPaddingDp(size) * 2

    /**
     * The scroll clearance a page must reserve while the centre style is chosen.
     *
     * The taller capsule replaces the rung's own height in the same arithmetic `clearanceDp` uses:
     * the dock's height, the air the reader chose under it, and the breathing room above the last
     * row. Without it, switching to the centre style would push the last row of every list under
     * the bar — the same clipping the V256 pass fixed, from the other side.
     */
    fun centerClearanceDp(size: DockSize, gapDp: Int = DEFAULT_BOTTOM_GAP_DP): Int =
        maxOf(BASE_CLEARANCE_DP, centerDockHeightDp(size) + bottomGapDp(gapDp) + CLEARANCE_BREATHING_DP)

    /**
     * How wide the trigger blooms while it is open: room for one glyph, the state word and the
     * latency, capped so the bloomed pill can never swallow the tabs on a 360dp phone.
     */
    fun centerBloomedWidthDp(size: DockSize): Int =
        (centerTriggerSizeDp(size) + 78).coerceAtMost(196)

    /** The glyph inside the trigger: one step above the tab glyph at every rung. */
    fun centerGlyphDp(size: DockSize): Int = iconSizeDp(size) + 2

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

    /**
     * MARBLE_FLOATING_DOCK_V258 — the ladder is now 56 / 68 / 82 dp of *capsule*.
     *
     * V256 shipped 48 / 56 / 64, and the report was right about it: eight dp between two rungs is
     * inside the noise of a screenshot. Ten to fourteen dp, a glyph that grows 20 → 24 → 28, and a
     * third rung that keeps its captions drawn are three differences a person can see side by side.
     */
    fun heightDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 56
        DockSize.MEDIUM -> 68
        DockSize.LARGE -> 82
    }

    /**
     * Inner air: 6 / 8 / 11 dp. The glyph never touches the rim, and the difference between two
     * rungs is what makes the ladder visible at a glance.
     */
    fun pillPaddingDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 6
        DockSize.MEDIUM -> 8
        DockSize.LARGE -> 11
    }

    /** 20 / 24 / 28 dp glyphs: the icon grows with the capsule, never past the row icon size. */
    fun iconSizeDp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 20
        DockSize.MEDIUM -> 24
        DockSize.LARGE -> 28
    }

    /** The word's rung: 10 / 11 / 13 sp, so a Grand capsule is not a Slim one with big glyphs. */
    fun labelSp(size: DockSize): Int = when (size) {
        DockSize.SMALL -> 10
        DockSize.MEDIUM -> 11
        DockSize.LARGE -> 13
    }

    /**
     * The third difference between the rungs, and the one a screenshot cannot miss: on the biggest
     * capsule the selected tab keeps its word drawn for as long as it is selected, the middle one
     * opens it for [EXPANDED_HOLD_MS] and then relaxes, and the smallest stays a pure icon bar.
     */
    fun showsCaptionsAlways(size: DockSize): Boolean = size == DockSize.LARGE

    /** A stadium, at every rung: the corner radius is exactly half the height. */
    fun cornerRadiusDp(size: DockSize): Float = heightDp(size) / 2f

    /**
     * Width of a tab while its word is showing: the resting circle plus the word's own room.
     * Derived from the height so a Grand dock's capsule is proportionally wider rather than
     * a Slim capsule with a long word squeezed into it.
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

    /** The user's chosen air under the capsule, clamped into the setting's own range. */
    fun bottomGapDp(gapDp: Int): Int = gapDp.coerceIn(MIN_BOTTOM_GAP_DP, MAX_BOTTOM_GAP_DP)

    /** The pill plus the air under it — what the bottom of the screen actually loses. */
    fun occupiedHeightDp(size: DockSize, gapDp: Int = DEFAULT_BOTTOM_GAP_DP): Int =
        heightDp(size) + bottomGapDp(gapDp)

    /**
     * The scroll clearance a page must reserve for this rung at this gap.
     *
     * It is derived, never a second constant that could drift: the dock's own height, the air the
     * user chose under it, and [CLEARANCE_BREATHING_DP] so the last row of a list does not touch
     * the rim. [BASE_CLEARANCE_DP] is the floor, so a slim dock at 8dp never makes the pages
     * tighter than they were before the gap existed.
     */
    fun clearanceDp(size: DockSize, gapDp: Int = DEFAULT_BOTTOM_GAP_DP): Int =
        maxOf(BASE_CLEARANCE_DP, occupiedHeightDp(size, gapDp) + CLEARANCE_BREATHING_DP)

    /**
     * Does this rung fit the clearance a page reserves?
     *
     * The argument order is deliberate: [clearanceDp] stays second, because that is the call every
     * existing site makes, and [gapDp] is the new axis.
     */
    fun fitsClearance(
        size: DockSize,
        clearanceDp: Int = BASE_CLEARANCE_DP,
        gapDp: Int = DEFAULT_BOTTOM_GAP_DP
    ): Boolean = occupiedHeightDp(size, gapDp) <= clearanceDp

    /** Every rung has to fit, or the settings page is offering a size the shell cannot honour. */
    fun ladderFits(clearanceDp: Int = BASE_CLEARANCE_DP): Boolean =
        DockSize.entries.all { fitsClearance(it, clearanceDp) }

    /** The settings labels, in ladder order, as the English keys `trx` translates. */
    fun ladderTitle(size: DockSize): String = when (size) {
        DockSize.SMALL -> "Slim"
        DockSize.MEDIUM -> "Classic"
        DockSize.LARGE -> "Grand"
    }

    /** One line per rung that says what is actually different, not only how tall it is. */
    fun ladderHint(size: DockSize): String = when (size) {
        DockSize.SMALL -> "Icons only, tightest capsule"
        DockSize.MEDIUM -> "Bigger glyphs, the selected word opens for a moment"
        DockSize.LARGE -> "Largest glyphs, the selected word never closes"
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
