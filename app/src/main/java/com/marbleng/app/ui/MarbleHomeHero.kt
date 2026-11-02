@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.HeroMetric
import com.marbleng.app.model.HeroMetricKind
import com.marbleng.app.model.HeroTone
import com.marbleng.app.model.HomeHeroSnapshot
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.onSuccessContainer
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.successContainer

/**
 * MARBLE_HOME_HERO_V261 — the top of Home, rebuilt as one flexible Material surface instead of a
 * stack of cards.
 *
 * What was wrong with the old hero is worth writing down, because the fix is a subtraction:
 *
 *  - It was **a stage and then a grid and then a strip**. Three surfaces, three corner radii, three
 *    ideas of what a shadow is. On a screenshot it read as a page that had been assembled.
 *  - It printed **one number** (`48 ms` *or* the uptime clock) in display type and pushed the rest to
 *    captions, so the four facts a reader actually opens the app for could never be seen together.
 *  - Its **shape was fixed**. A 34/16 asymmetric squircle is a beautiful shape for a fixed amount of
 *    content and the wrong shape for a surface that grows a warm-up row, a mode row and a route pill
 *    depending on the style the reader chose.
 *
 * The V261 hero is a single **hero panel**:
 *
 *  1. **One container, one radius.** [MarbleHeroPanel] is a tall Expressive surface with the app's
 *     asymmetric corner *and* a tonal wash in the state's own hue, so the whole top of the page reads
 *     as one object. Everything below it on Home is deliberately smaller and quieter.
 *  2. **A metrics grid inside the panel.** [HeroMetricGrid] draws the four facts
 *     ([HomeHeroSnapshot.metrics]) as two rows of two *cells of the same surface* — separated by the
 *     panel's own air rather than by four cards with four radii, which is what made the old page
 *     look tiled.
 *  3. **A tone per cell, not a colour per cell.** The cell's ink and its dot come from
 *     [HeroTone], so a bad jitter is amber and a good one is mint *in every palette*, and a metric
 *     nobody has measured is a quiet dash instead of a proud zero.
 *  4. **Flexible by construction.** The grid is a list of whatever the page handed it; the panel does
 *     not care whether that is four metrics, four metrics and a warm-up row, or a hero that also
 *     carries the connect pill. The old hero's layout was written around exactly three rows.
 *
 * The panel is deliberately *not* clickable as a whole: each metric cell is its own target (ping
 * triggers a measurement, the exit address opens the IP page), and a surface-wide click underneath
 * those would fire for every miss. The one whole-surface affordance a reader needs — the logs — stays
 * a labelled capsule in the panel's action row, where it can be read before it is pressed.
 */

/**
 * The panel: the state's colour, the motif, the headline, the metrics, and whatever action row the
 * caller needs. One surface, one radius, one lift.
 */
@Composable
internal fun MarbleHeroPanel(
    snapshot: HomeHeroSnapshot,
    motif: MarbleExpressiveMotif,
    onMetricClick: (HeroMetricKind) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLogs: (() -> Unit)? = null,
    onOpenRoute: (() -> Unit)? = null,
    primaryAction: (@Composable () -> Unit)? = null,
    tag: String = "home-hero-panel"
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val connected = snapshot.metrics.any { it.kind == HeroMetricKind.UPTIME && it.tone == HeroTone.LIVE }
    val working = snapshot.working

    val stageColor by animateColorAsState(
        targetValue = when {
            connected -> colors.successContainer
            working -> colors.tertiaryContainer
            else -> colors.primaryContainer
        },
        animationSpec = marbleDefaultSpatialSpec(),
        label = "heroPanelStage"
    )
    val onStage = when {
        connected -> colors.onSuccessContainer
        working -> colors.onTertiaryContainer
        else -> colors.onPrimaryContainer
    }
    val stateLight = if (connected) colors.success else colors.primary
    val morph by animateFloatAsState(
        targetValue = when {
            connected -> 1f
            working -> 0.5f
            else -> 0f
        },
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "heroPanelMorph"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(marbleAsymmetricShape(prominent = true))
            .background(stageColor)
            .testTag(tag)
    ) {
        // The state's own light, painted into the panel rather than layered over it: two radial
        // washes in the state's hue, so the surface has a direction without a border.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stateLight.copy(alpha = if (connected) 0.30f else 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.86f, size.height * 0.08f),
                    radius = size.width * 0.82f
                ),
                radius = size.width * 0.82f,
                center = Offset(size.width * 0.86f, size.height * 0.08f)
            )
        }
        MarblePetalMark(
            color = onStage.copy(alpha = if (connected || working) 0.16f else 0.10f),
            size = 176.dp,
            motif = motif,
            targetMotif = MarbleExpressiveMotif.FLOWER,
            morphProgress = morph,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationX = 42.dp.toPx()
                    translationY = (-44).dp.toPx()
                }
        )

        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── the head: the state, and the route it is about ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // A small state light beside the overline, so the panel says *what* it is
                        // before it says how much: the same pairing the status row uses everywhere.
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(stateLight)
                        )
                        Text(
                            text = snapshot.overline,
                            color = onStage.copy(alpha = 0.74f),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AnimatedContent(
                        targetState = snapshot.stateLabel,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith
                                (fadeOut() + scaleOut(targetScale = 0.94f))
                        },
                        label = "heroPanelState"
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

                if (onOpenLogs != null) {
                    HeroLogsCapsule(
                        connected = connected,
                        working = working,
                        onStage = onStage,
                        onClick = onOpenLogs
                    )
                }
            }

            // ── the route strip: the answer to "connected to what?" ───────────────────────────
            //
            // MARBLE_HOME_HERO_V261 — the strip has its own destination and the exit *cell* has
            // another. They used to share one: the strip's tap opened the IP page, which is a page
            // about the address, not about the server the strip names. Two questions, two pages.
            HeroRouteStrip(
                snapshot = snapshot,
                onStage = onStage,
                onClick = onOpenRoute ?: { onMetricClick(HeroMetricKind.EXIT) }
            )

            // ── the metrics: four facts, one surface ──────────────────────────────────────────
            HeroMetricGrid(
                metrics = snapshot.metrics,
                onStage = onStage,
                onMetricClick = onMetricClick
            )

            primaryAction?.invoke()
        }
    }
}

/** The four facts, two by two. Both cells of a row share the height of the taller one. */
@Composable
internal fun HeroMetricGrid(
    metrics: List<HeroMetric>,
    onStage: Color,
    onMetricClick: (HeroMetricKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-hero-metrics"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metrics.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { metric ->
                    HeroMetricCell(
                        metric = metric,
                        onStage = onStage,
                        onClick = { onMetricClick(metric.kind) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                // An odd count leaves the last cell alone in its row rather than stretching it over
                // the whole width: a grid of two columns that sometimes has one full-width row is
                // not a grid, and the four shipped metrics are always even.
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * One cell. Numbers are set in the display face with their unit beside them in label type — which is
 * the whole reason [HeroMetric] keeps the digits and the unit apart — and the tone decides the dot
 * and, for a bad reading, the ink.
 */
@Composable
private fun HeroMetricCell(
    metric: HeroMetric,
    onStage: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val tone = metric.tone
    val accent = when (tone) {
        HeroTone.GOOD -> colors.success
        HeroTone.FAIR -> colors.warning
        HeroTone.POOR -> colors.error
        HeroTone.WORKING -> onStage
        HeroTone.LIVE -> onStage
        HeroTone.PENDING, HeroTone.IDLE -> onStage.copy(alpha = 0.55f)
    }
    val numeric = metric.kind != HeroMetricKind.EXIT && metric.kind != HeroMetricKind.UPTIME
    val isMono = metric.kind == HeroMetricKind.EXIT || metric.kind == HeroMetricKind.UPTIME
    val label = when (metric.kind) {
        HeroMetricKind.PING -> trx("Ping")
        HeroMetricKind.JITTER -> trx("Jitter")
        HeroMetricKind.UPTIME -> trx("Uptime")
        HeroMetricKind.EXIT -> trx("Exit IP")
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(onStage.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("home-hero-metric-${metric.kind.id}")
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(accent)
            )
            Text(
                text = label,
                color = onStage.copy(alpha = 0.70f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (metric.isWorking) {
            LoadingIndicator(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(20.dp),
                color = onStage
            )
        } else {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = if (numeric) faDigits(metric.value) else metric.value,
                    color = if (tone == HeroTone.POOR) accent else onStage,
                    style = if (isMono) {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.headlineSmallEmphasized
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (metric.unit.isNotBlank()) {
                    Text(
                        // The unit is Latin "ms" in both languages: it is a symbol, like a degree
                        // sign, and translating it would be translating a measurement's name.
                        text = metric.unit,
                        color = onStage.copy(alpha = 0.64f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

/** The route strip: avatar, name, protocol, and a chevron — the server this panel is about. */
@Composable
private fun HeroRouteStrip(
    snapshot: HomeHeroSnapshot,
    onStage: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(onStage.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("home-hero-route")
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MarbleServerAvatar(
            profile = null,
            countryCode = snapshot.countryCode,
            size = 30.dp
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = snapshot.routeName,
                color = onStage,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (snapshot.routeDetail.isNotBlank()) {
                Text(
                    text = snapshot.routeDetail,
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

/** The panel's one labelled action: the logs, in the corner where a status light would sit. */
@Composable
private fun HeroLogsCapsule(
    connected: Boolean,
    working: Boolean,
    onStage: Color,
    onClick: () -> Unit
) {
    val logsLabel = trx("Logs")
    val health = when {
        connected -> ServerHealth.GOOD
        working -> ServerHealth.SLOW
        else -> ServerHealth.UNTESTED
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(onStage.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = logsLabel }
            .testTag("home-hero-logs")
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MarbleHealthDot(health = health, size = 8.dp)
        Text(
            text = logsLabel,
            color = onStage,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}
