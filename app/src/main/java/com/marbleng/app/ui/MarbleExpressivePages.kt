@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.marbleng.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.settleSpec

/**
 * MARBLE_EXPRESSIVE_PAGES_V251 — the inner page of every Settings destination.
 *
 * V249 gave the hub a bento and the drill-in a zoom, and then dropped the reader on a page built
 * from the same plain cards the hub had just stopped using: a medium top bar, a list, and no
 * identity of its own. This scaffold is that page's missing first impression, and it is shared by
 * every destination so a settings page can never look like it belongs to a different app:
 *
 * 1. **a hero** — the destination's own glyph in a Material shape, its title, and one sentence that
 *    says what the page changes. It collapses *into* the top bar as the list scrolls, on the app's
 *    own pan curve, and its halo recedes with the same fraction, so the page has depth instead of
 *    a static header;
 * 2. **the bar** — the same tonal bar the rest of the app uses, with the mirrored back affordance;
 * 3. **the rhythm** — [MarbleSpec.SECTION_SPACING_DP] between sections, the page's own bottom
 *    clearance, and no hairlines.
 *
 * `content` receives a [LazyListScope] exactly like [MarbleSubPageScaffold], so a page opts in by
 * changing its call, not its structure.
 */
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (onContentScrollChanged != null) {
        LaunchedEffect(listState.isScrollInProgress) {
            onContentScrollChanged(listState.isScrollInProgress)
        }
    }

    // The halo, the hero glyph's scale and the subtitle's alpha all read the same fraction, so a
    // scroll moves one object rather than three that happen to start together.
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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // Transparent on purpose: the shell draws the ambient light, and a page that filled its
        // own background would cover it at the one place a settings page is most of the screen.
        containerColor = Color.Transparent,
        topBar = {
            MediumTopAppBar(
                title = {
                    // The title lives in the hero while the hero is on screen, and fades into the
                    // bar exactly as the hero leaves: one title, two places, one opacity.
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
                colors = TopAppBarDefaults.mediumTopAppBarColors(
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
                top = 4.dp,
                bottom = bottomClearance
            ),
            verticalArrangement = Arrangement.spacedBy(MarbleSpec.SECTION_SPACING_DP.dp)
        ) {
            item(key = "expressive-page-hero") {
                ExpressivePageHero(
                    title = title,
                    subtitle = subtitle,
                    glyph = glyph,
                    tint = tint,
                    collapse = collapse,
                    heroScale = heroScale
                )
            }
            content()
            item(key = "expressive-page-tail") {
                Spacer(Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * MARBLE_EXPRESSIVE_PAGES_V251 — the app's own light.
 *
 * Every screen sits on it: two very wide, very soft radial washes in the theme's primary and
 * tertiary tones, drifting a few dozen pixels over half a minute. It is *behind* the content at
 * single-digit alpha, so it never competes with a card, and it is the same drawing everywhere —
 * which is what makes four tabs read as one surface instead of four screens that share a colour
 * scheme.
 *
 * It is decorative, so it obeys the motion switch: calm motion freezes it, reduced motion removes
 * the drift entirely, and a true-black theme gets nothing at all (a wash on AMOLED is a glow around
 * a black rectangle, not depth).
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

/** The hero: shape, glyph, title, one sentence, and the same bloom the bento tiles carry. */
@Composable
private fun ExpressivePageHero(
    title: String,
    subtitle: String,
    glyph: MarbleGlyph,
    tint: Color,
    collapse: Float,
    heroScale: Float
) {
    val colors = MaterialTheme.colorScheme
    val fraction = collapse.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = 0.16f),
                        colors.surfaceContainerLow
                    )
                )
            )
            .padding(16.dp)
    ) {
        MarbleOrbitLens(
            tint = tint,
            radiusFraction = 0.40f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(96.dp)
                .graphicsLayer { alpha = (1f - fraction) * 0.9f }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                    }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(tint.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = glyph,
                    color = tint,
                    modifier = Modifier.size(24.dp),
                    weight = 1.1f
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
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
