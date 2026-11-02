@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.HomeSection
import com.marbleng.app.ui.theme.marbleMotion

/**
 * MARBLE_HOME_QUIET_V259 / MARBLE_HOME_DECLUTTER_V260 — Home's first-run furniture.
 *
 * The layout problem the previous Home had was not that it drew the wrong cards; it was that it drew
 * *all* of them, in one column, at one weight. A heading, a hero, a strip, a route row, a graph and
 * four tiles are six answers of equal loudness, and a reader cannot tell which one the page is
 * about.
 *
 * The one loud surface used to be here, as [MarbleCalmStage]. MARBLE_HOME_HERO_V261 moved it to
 * `MarbleHomeHero.kt` and renamed it [MarbleHeroPanel], because it grew a metrics grid and stopped
 * being a *calm* stage: it is now the hero, and it lives beside the grid it draws. This file keeps the
 * two pieces that are genuinely first-run furniture:
 *
 *  - [MarbleWarmupAction] — tonal keys that fill an empty library. They exist because a quiet Home
 *    with no server is a dead end, and the old page answered that with a dashboard of features the
 *    reader could not use yet.
 *  - [MarbleHomeSectionPreview] — the studio's live preview of the Home order, which is a *Settings*
 *    surface and has nothing to do with a first run but shares the file's "no repository" rule.
 *
 * V260 removed the third piece — the "A quiet Home" card with its `3/6` counter and its two verbs. A
 * page that explains how modest it is has stopped being modest; the switches live in Settings, where a
 * reader who wants them is already looking.
 *
 * Nothing here reads a repository: the pages own the state and hand it over, which is what keeps
 * this file drawable in a preview and testable without a device.
 */

/**
 * MARBLE_HOME_QUIET_V259 / MARBLE_HOME_DECLUTTER_V260 — the first-run warm-up: two tonal keys.
 *
 * Shown only while the library is empty, and deliberately the same size and weight as each other so
 * they read as one choice with two doors. Each one is a verb, not a noun ("Paste a link", "Scan a
 * QR"), because the reader's question at this point is "how do I get one server in here?".
 *
 * V260 removed the third key. "Add manually" opened the same Add sheet the header's `+` opens, and
 * that sheet's first row is the manual form — three paths to one form on a page that is supposed to
 * be the quiet one. The two keys that stay are the two the page cannot otherwise offer for free: the
 * clipboard it already holds, and the code it can read.
 */
@Composable
internal fun MarbleWarmupRow(
    onPaste: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-warmup"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MarbleWarmupAction(
            title = trx("Paste a link"),
            glyph = MarbleGlyph.COPY,
            motif = MarbleExpressiveMotif.CLOVER,
            tint = MaterialTheme.colorScheme.primary,
            onClick = onPaste,
            modifier = Modifier.weight(1f)
        )
        MarbleWarmupAction(
            title = trx("Scan a QR"),
            glyph = MarbleGlyph.QR,
            motif = MarbleExpressiveMotif.DIAMOND,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = onScan,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MarbleWarmupAction(
    title: String,
    glyph: MarbleGlyph,
    motif: MarbleExpressiveMotif,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (motion.still) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "homeWarmupPress"
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(marbleAsymmetricShape(prominent = false))
            .background(colors.surfaceContainerLow)
            .clickable(
                role = Role.Button,
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(marbleExpressiveShape(motif))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            MarbleGlyphIcon(
                glyph = glyph,
                color = tint,
                modifier = Modifier.size(16.dp),
                weight = 1.15f
            )
        }
        Text(
            text = title,
            color = colors.onSurface,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * MARBLE_HOME_QUIET_V259 — the studio's preview strip.
 *
 * The Home layout page is a list of switches, and a list of switches says nothing about what the page
 * will look like. This strip draws the six sections as six chips in their real order, filled when
 * they are drawn and hollow when they are off, so the page answers "what am I about to get?" on the
 * same screen as the switch that changes it.
 */
@Composable
internal fun MarbleHomeSectionPreview(
    order: List<HomeSection>,
    hiddenRaw: String,
    legacyStatsVisible: Boolean,
    legacyShortcutsVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-section-preview"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        order.forEach { section ->
            val visible = !com.marbleng.app.model.HomeStudio.isHidden(
                section = section,
                hiddenRaw = hiddenRaw,
                legacyStatsVisible = legacyStatsVisible,
                legacyShortcutsVisible = legacyShortcutsVisible
            )
            Box(
                modifier = Modifier
                    .weight(
                        when (section) {
                            HomeSection.CONNECT -> 2.2f
                            HomeSection.ROUTE -> 1.6f
                            else -> 1f
                        }
                    )
                    .heightIn(min = 30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (visible) colors.primary.copy(alpha = 0.85f)
                        else colors.surfaceContainerHighest
                    )
            )
        }
    }
}

/**
 * MARBLE_HOME_QUIET_V259 — "3 of 6" for the studio's header badge, and the calm-state badge Home
 * prints. One function so the two can never round differently.
 */
@Composable
internal fun homeQuietShownLabel(shown: Int, total: Int = HomeSection.entries.size): String =
    faDigits("$shown/$total")

/** The warm-up's own visibility rule, named once: only while there is nothing to connect to. */
internal fun homeShowsWarmup(profileCount: Int): Boolean = profileCount <= 0

