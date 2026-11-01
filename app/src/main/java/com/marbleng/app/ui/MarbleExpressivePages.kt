@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.marbleng.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleDefaultEffectsSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.settleSpec

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 — shared inner-page scaffold and inline umbrella switcher.
 *
 * Every Settings sub-page sits inside one of the seven broader categories ([SettingsUmbrellaGroup]).
 * [LocalSettingsUmbrellaNav] threads that parent category into [MarbleExpressivePageScaffold] so the
 * hero names the broader umbrella, morphs its Material 3 Expressive polygon per sub-topic, and
 * mounts an inline morphing topic bar directly beneath the hero — eliminating pogo-sticking back to
 * the Settings root just to change an adjacent option.
 */
@Immutable
internal data class SettingsUmbrellaNavState(
    val group: SettingsUmbrellaGroup,
    val active: SettingsDestination,
    val onSelect: (SettingsDestination) -> Unit
)

internal val LocalSettingsUmbrellaNav = staticCompositionLocalOf<SettingsUmbrellaNavState?> { null }

@Composable
internal fun MarbleExpressivePageScaffold(
    title: String,
    onBack: () -> Unit,
    glyph: MarbleGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    backLabel: String = Tr.now.backLabel,
    trailing: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    bottomClearance: Dp = MarbleTheme.bottomClearance,
    onContentScrollChanged: ((Boolean) -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val umbrella = LocalSettingsUmbrellaNav.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (onContentScrollChanged != null) {
        LaunchedEffect(listState.isScrollInProgress) {
            onContentScrollChanged(listState.isScrollInProgress)
        }
    }

    val collapse by remember {
        derivedStateOf {
            collapseFractionOf(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            )
        }
    }
    val heroScale by animateFloatAsState(
        targetValue = 1f - collapse * 0.18f,
        animationSpec = if (motion.still) snap() else motion.settleSpec(220),
        label = "settingsHeroScale"
    )
    val heroMotif = umbrella?.let { SettingsHubLayout.motifFor(it.active) }
        ?: MarbleExpressiveMotif.forGlyph(glyph)
    val umbrellaTitle = umbrella?.let { trx(it.group.titleKey) }.orEmpty()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            // MARBLE_SETTINGS_TOP_AIR_V259 — the reported gap.
            //
            // A `MediumTopAppBar` is 112dp at rest *by design*: it reserves a large title area under
            // the status bar and shrinks to 64dp as the list scrolls. On an inner Settings page that
            // reservation was spent on nothing — the title is already drawn 24dp below in the hero —
            // so the page opened with an empty band above its own heading and the heading sat far
            // from the top of the app. It is now the standard `TopAppBar`: one 64dp row that holds the
            // back arrow, the collapsed title and the page's own trailing action, with the hero
            // immediately under it. The scroll behaviour is unchanged, so the title still fades in as
            // the hero collapses.
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            alpha = collapse.coerceIn(0f, 1f)
                            translationY = (1f - collapse) * 24f
                        }
                    )
                },
                navigationIcon = {
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    IconButton(onClick = onBack) {
                        MarbleGlyphIcon(
                            glyph = if (isRtl) MarbleGlyph.CHEVRON else MarbleGlyph.CHEVRON_LEFT,
                            color = colors.onSurface,
                            modifier = Modifier.size(22.dp),
                            weight = 1.15f
                        )
                    }
                },
                actions = { trailing?.invoke() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    scrolledContainerColor = colors.surfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                top = 0.dp,
                bottom = bottomClearance
            ),
            verticalArrangement = Arrangement.spacedBy(MarbleSpec.SECTION_SPACING_DP.dp)
        ) {
            item(key = "expressive-page-hero") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExpressivePageHero(
                        overline = umbrellaTitle,
                        title = title,
                        subtitle = subtitle,
                        glyph = glyph,
                        motif = heroMotif,
                        tint = tint,
                        collapse = collapse,
                        heroScale = heroScale
                    )
                    if (umbrella != null && umbrella.group.destinations.size > 1) {
                        SettingsUmbrellaTopicBar(umbrella = umbrella)
                    }
                }
            }
            content()
            item(key = "expressive-page-tail") {
                Spacer(Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun SettingsUmbrellaTopicBar(
    umbrella: SettingsUmbrellaNavState
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        umbrella.group.destinations.forEach { dest ->
            val selected = dest == umbrella.active
            val label = trx(SettingsHubLayout.shortTopicTitleKey(dest))
            val motif = SettingsHubLayout.motifFor(dest)
            val shape = marbleSelectionMorphShape(
                selected = selected,
                idleDp = 14.dp,
                selectedDp = 28.dp
            )
            val container by animateColorAsState(
                targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                animationSpec = marbleDefaultEffectsSpec(),
                label = "topicChipBg"
            )
            val ink = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(container)
                    .clickable(role = Role.Tab) {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            umbrella.onSelect(dest)
                        }
                    }
                    .testTag("settings-topic-${dest.name}")
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 16.dp else 10.dp)
                        .clip(marbleExpressiveShape(motif))
                        .background(
                            if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.35f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        MarbleGlyphIcon(
                            glyph = MarbleGlyph.CHECK,
                            color = colors.onPrimary,
                            modifier = Modifier.size(10.dp),
                            weight = 1.3f
                        )
                    }
                }
                Text(
                    text = label,
                    color = ink,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * MARBLE_EXPRESSIVE_PAGES_V251 — the app's own light.
 */
@Composable
fun MarbleAmbientBackdrop(
    modifier: Modifier = Modifier,
    trueBlack: Boolean = false
) {
    if (trueBlack) return
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val drift = if (motion.loops) {
        val transition = rememberInfiniteTransition(label = "ambientBackdrop")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 26_000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambientDrift"
        )
        value
    } else {
        0.36f
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (motion.still) 0.6f else 1f }
    ) {
        val w = size.width
        val h = size.height
        val shift = (drift - 0.5f) * 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.primary.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * (0.18f + shift * 0.06f), h * (0.12f - shift * 0.04f)),
                radius = w * 0.95f
            ),
            radius = w * 0.95f,
            center = Offset(w * (0.18f + shift * 0.06f), h * (0.12f - shift * 0.04f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.tertiary.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(w * (0.92f - shift * 0.05f), h * (0.72f + shift * 0.05f)),
                radius = w * 1.05f
            ),
            radius = w * 1.05f,
            center = Offset(w * (0.92f - shift * 0.05f), h * (0.72f + shift * 0.05f))
        )
    }
}

/** The hero: expressive polygon badge, umbrella overline, title, subtitle, and morphing bloom. */
@Composable
private fun ExpressivePageHero(
    overline: String,
    title: String,
    subtitle: String,
    glyph: MarbleGlyph,
    motif: MarbleExpressiveMotif,
    tint: Color,
    collapse: Float,
    heroScale: Float
) {
    val colors = MaterialTheme.colorScheme
    val fraction = collapse.coerceIn(0f, 1f)
    val heroShape = marbleAsymmetricShape(prominent = true)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = 0.16f),
                        colors.surfaceContainerLow
                    )
                )
            )
            .padding(vertical = 10.dp, horizontal = 18.dp)
    ) {
        MarbleOrbitLens(
            tint = tint,
            motif = motif,
            radiusFraction = 0.42f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(104.dp)
                .graphicsLayer { alpha = (1f - fraction) * 0.92f }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                    }
                    .clip(marbleExpressiveShape(motif))
                    .background(tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = glyph,
                    color = tint,
                    modifier = Modifier.size(24.dp),
                    weight = 1.12f
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (overline.isNotBlank()) {
                    Text(
                        text = overline,
                        color = tint,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = colors.onSurfaceVariant.copy(alpha = 1f - fraction * 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}
