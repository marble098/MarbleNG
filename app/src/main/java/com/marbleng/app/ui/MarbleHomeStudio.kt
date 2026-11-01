@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.HomeSection
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.onSuccessContainer
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.successContainer

/**
 * MARBLE_HOME_QUIET_V259 — Home's new furniture: the calm stage, the calm menu card, and the two
 * warm-up actions a first run actually needs.
 *
 * The layout problem the previous Home had was not that it drew the wrong cards; it was that it drew
 * *all* of them, in one column, at one weight. A heading, a hero, a strip, a route row, a graph and
 * four tiles are six answers of equal loudness, and a reader cannot tell which one the page is
 * about. This file gives the page a hierarchy instead:
 *
 *  - [MarbleCalmStage] is the one loud surface — a full-width Expressive asymmetrical stage whose
 *    colour *is* the state, whose orbit lens turns behind the title, and whose readout zone swaps
 *    between a latency gauge and the protected clock. Everything else on Home is quieter than it.
 *  - [MarbleQuietCard] is the "Home is quiet" card: it says what is hidden, and offers the two
 *    things a reader who wants more would ask for — show everything, or open the studio.
 *  - [MarbleWarmupAction] is first-run furniture: three tonal keys that fill an empty library. They
 *    exist because a quiet Home with no server is a dead end, and the old page answered that with a
 *    dashboard of features the reader could not use yet.
 *
 * Nothing here reads a repository: the pages own the state and hand it over, which is what keeps
 * this file drawable in a preview and testable without a device.
 */

/**
 * The calm Home's stage: the one loud surface on the page.
 *
 * Four decisions are visible in the shape:
 *
 *  1. **The container is asymmetric.** `marbleAsymmetricShape(prominent = true)` is 34dp on three
 *     corners and 16dp on the fourth, so the stage leans towards its own readout instead of sitting
 *     in the middle of the page like a card.
 *  2. **The colour is the state.** `successContainer` while protected, `tertiaryContainer` while
 *     working, `primaryContainer` at rest — a whole surface, not a pill beside a word.
 *  3. **The readout is one number, big.** Latency while at rest, the protected clock while
 *     connected, a progress ring while working. `AnimatedContent` swaps them, so the stage never
 *     grows a second line to explain itself.
 *  4. **The lens is the app's own material.** [MarblePetalMark] morphs `Clover4Leaf → Flower` behind
 *     the title, turning slowly on the same spring the rest of the product uses, and holding still
 *     when the reader asked for calm motion.
 *
 * The stage is a *reading* when the connect control lives in the dock and a *control* when it does
 * not, which is why the click is passed in rather than assumed: the two callers differ.
 */
@Composable
internal fun MarbleCalmStage(
    connected: Boolean,
    working: Boolean,
    stateLabel: String,
    overline: String,
    readout: String,
    readoutCaption: String,
    readoutLoading: Boolean,
    routeProfile: com.marbleng.app.model.ProxyProfile?,
    routeCountryCode: String?,
    routeName: String,
    routeDetail: String,
    motif: MarbleExpressiveMotif,
    primaryAction: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "home-calm-stage"
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()

    val stageColor by animateColorAsState(
        targetValue = when {
            connected -> colors.successContainer
            working -> colors.tertiaryContainer
            else -> colors.primaryContainer
        },
        animationSpec = marbleDefaultSpatialSpec(),
        label = "homeCalmStage"
    )
    val onStage = when {
        connected -> colors.onSuccessContainer
        working -> colors.onTertiaryContainer
        else -> colors.onPrimaryContainer
    }
    val morph by animateFloatAsState(
        targetValue = when {
            connected -> 1f
            working -> 0.5f
            else -> 0f
        },
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "homeCalmStageMorph"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(marbleAsymmetricShape(prominent = true))
            .background(stageColor)
    ) {
        // The state's own light, painted into the stage rather than layered over it: two radial
        // washes in the state's own hue, so the surface has a direction without a border.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (connected) colors.success.copy(alpha = 0.30f) else colors.primary.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.86f, size.height * 0.10f),
                    radius = size.width * 0.78f
                ),
                radius = size.width * 0.78f,
                center = Offset(size.width * 0.86f, size.height * 0.10f)
            )
        }
        MarblePetalMark(
            color = onStage.copy(alpha = if (connected || working) 0.16f else 0.10f),
            size = 168.dp,
            motif = motif,
            targetMotif = MarbleExpressiveMotif.FLOWER,
            morphProgress = morph,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationX = 40.dp.toPx()
                    translationY = (-42).dp.toPx()
                }
        )

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = overline,
                        color = onStage.copy(alpha = 0.74f),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                    AnimatedContent(
                        targetState = stateLabel,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                                (fadeOut() + scaleOut(targetScale = 0.92f))
                        },
                        label = "homeCalmState"
                    ) { label ->
                        Text(
                            text = trx(label),
                            color = onStage,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // The readout column: one number and its caption, right-aligned against the stage's
                // anchor corner. It is the only place on Home that prints a measurement in display
                // type, which is what makes the page readable at a glance.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    if (readoutLoading) {
                        LoadingIndicator(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .size(26.dp),
                            color = onStage
                        )
                    } else {
                        AnimatedContent(
                            targetState = readout,
                            transitionSpec = {
                                (fadeIn() + scaleIn(initialScale = 0.86f)) togetherWith
                                    (fadeOut() + scaleOut(targetScale = 0.86f))
                            },
                            label = "homeCalmReadout"
                        ) { value ->
                            Text(
                                text = value,
                                color = onStage,
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                maxLines = 1
                            )
                        }
                    }
                    if (readoutCaption.isNotBlank()) {
                        Text(
                            text = readoutCaption,
                            color = onStage.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }

            primaryAction()

            // The route strip: the answer to "connected to what?", in one row, at the bottom of the
            // stage where a status bar would be. It is a *reading* here, not a destination — the
            // stage's click already goes to the logs.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(onStage.copy(alpha = 0.10f))
                    .clickable(role = Role.Button, onClick = onClick)
                    .testTag("$tag-route")
                    .padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MarbleServerAvatar(
                    profile = routeProfile,
                    countryCode = routeCountryCode,
                    size = 30.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = routeName,
                        color = onStage,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (routeDetail.isNotBlank()) {
                        Text(
                            text = routeDetail,
                            color = onStage.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                MarbleChevron(size = 14.dp, tint = onStage.copy(alpha = 0.8f))
            }
        }
    }
}

/**
 * MARBLE_HOME_QUIET_V259 — the card that says Home is quiet, and offers the way out of it.
 *
 * A page that hides three of its six sections has to *say so*, otherwise the quiet default reads as
 * a missing feature rather than a choice. Two verbs, deliberately different sizes: "Show everything"
 * is one tap that restores the full designed page, and "Choose sections" opens the studio where each
 * block is a row with its own switch. The count is printed because a reader who has already turned
 * one thing on needs to know what is left.
 */
@Composable
internal fun MarbleQuietCard(
    shownCount: Int,
    totalCount: Int,
    onShowEverything: () -> Unit,
    onChooseSections: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val tones = marbleExpressiveTones()
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(marbleAsymmetricShape(prominent = false))
            .background(tones.lavender.container)
            .testTag("home-quiet-card")
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(marbleExpressiveShape(MarbleExpressiveMotif.COOKIE))
                    .background(tones.lavender.badge.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.LAYERS,
                    color = tones.lavender.badge,
                    modifier = Modifier.size(20.dp),
                    weight = 1.12f
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = trx("A quiet Home"),
                    color = tones.lavender.content,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1
                )
                Text(
                    text = trx("Only what you need is here. Add the rest whenever you want it."),
                    color = tones.lavender.content.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = faDigits("$shownCount/$totalCount"),
                color = tones.lavender.content.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarbleQuietVerb(
                label = trx("Show everything"),
                filled = true,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onShowEverything()
                },
                modifier = Modifier.weight(1f)
            )
            MarbleQuietVerb(
                label = trx("Choose sections"),
                filled = false,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onChooseSections()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MarbleQuietVerb(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (filled) colors.primary else colors.primary.copy(alpha = 0.10f)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = if (filled) colors.onPrimary else colors.primary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * MARBLE_HOME_QUIET_V259 — the first-run warm-up: three tonal keys that fill an empty library.
 *
 * Shown only while the library is empty, and deliberately the same size and weight as each other so
 * they read as one choice with three doors rather than three features. Each one is a verb, not a
 * noun ("Paste a link", "Scan a QR", "Add manually"), because the reader's question at this point is
 * "how do I get one server in here?" and not "what is a subscription?".
 */
@Composable
internal fun MarbleWarmupRow(
    onPaste: () -> Unit,
    onScan: () -> Unit,
    onAdd: () -> Unit,
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
        MarbleWarmupAction(
            title = trx("Add manually"),
            glyph = MarbleGlyph.PLUS,
            motif = MarbleExpressiveMotif.SUNNY,
            tint = MaterialTheme.colorScheme.tertiary,
            onClick = onAdd,
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

/** Whether Home should offer the quiet card: only while something is actually hidden. */
internal fun homeShowsQuietCard(shown: Int, total: Int = HomeSection.entries.size): Boolean =
    shown < total
