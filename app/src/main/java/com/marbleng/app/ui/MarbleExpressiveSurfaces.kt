@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion

/**
 * MARBLE_EXPRESSIVE_BENTO_V250 — shared expressive surfaces for Home, Servers and Settings.
 *
 * The Lumen release is the visual reference, not a source dependency: modular tonal tiles,
 * a clear connection stage, and a reserved trailing action column. Everything here is built
 * on the app's existing Material 3 Expressive theme, motion scheme and Persian typography.
 */

/**
 * A fixed trailing action column. Selection marks, long titles and metadata live to the start
 * of this column, so they can never push the overflow/edit target out of alignment.
 */
@Composable
internal fun MarbleActionColumn(
    modifier: Modifier = Modifier,
    tag: String = "",
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .width(MarbleSpec.HEADER_ACTION_SIZE_DP.dp)
            .heightIn(min = MarbleSpec.HEADER_ACTION_SIZE_DP.dp)
            .then(if (tag.isBlank()) Modifier else Modifier.testTag(tag)),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * A decorative Material shape. It carries no semantics and never intercepts a touch.
 *
 * MARBLE_EXPRESSIVE_BLOOM_V251 — it also breathes: the cookie turns a slow quarter circle and its
 * radius swells a few percent, so a page of tiles is *alive* rather than a grid of stickers. Both
 * movements are decorative by definition, so [MarbleMotionStyle.loops] switches them off — a calm
 * or reduced-motion reader gets the resting frame instead.
 */
@Composable
internal fun MarblePetalMark(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animated: Boolean = true
) {
    val motion = marbleMotion()
    val floating = animated && motion.loops
    val transition = rememberInfiniteTransition(label = "petalBloom")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 22_000)),
        label = "petalTurn"
    )
    val bloom by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "petalBloom"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = if (floating) turn else 0f
                scaleX = if (floating) bloom else 1f
                scaleY = if (floating) bloom else 1f
            }
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(color)
    )
}

/**
 * MARBLE_EXPRESSIVE_BLOOM_V251 — one travelling arc inside a tile: a hairline ring with a bead that
 * goes round it once every few seconds. Drawn with the tile's own accent at low alpha, so it reads
 * as light on a surface rather than as a second control.
 */
@Composable
internal fun MarbleOrbitLens(
    tint: Color,
    modifier: Modifier = Modifier,
    periodMs: Int = 7_000,
    radiusFraction: Float = 0.42f
) {
    val motion = marbleMotion()
    if (!motion.loops) {
        Canvas(modifier = modifier) {
            drawCircle(
                color = tint.copy(alpha = 0.10f),
                radius = size.minDimension * radiusFraction,
                style = Stroke(width = 1f)
            )
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "orbitLens")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = periodMs)),
        label = "orbitLensAngle"
    )
    Canvas(modifier = modifier) {
        val radius = size.minDimension * radiusFraction
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = tint.copy(alpha = 0.10f), radius = radius, center = center, style = Stroke(width = 1f))
        val radians = Math.toRadians(angle.toDouble())
        drawCircle(
            color = tint.copy(alpha = 0.55f),
            radius = 3.5f,
            center = Offset(
                center.x + (radius * kotlin.math.cos(radians)).toFloat(),
                center.y + (radius * kotlin.math.sin(radians)).toFloat()
            )
        )
    }
}

/**
 * One tonal navigation tile. Featured tiles span a bento row; compact tiles share a row.
 * The whole card is a single 48dp+ button, with a springy corner/press morph that collapses
 * to a static frame when the user or the platform asks for reduced motion.
 */
@Composable
internal fun MarbleExpressiveTile(
    title: String,
    glyph: MarbleGlyph,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    featured: Boolean = false,
    enabled: Boolean = true,
    tag: String = ""
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val motion = marbleMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 18.dp else if (featured) 32.dp else 26.dp,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveTileCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) MarbleSpec.PRESSED_SCALE else 1f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveTilePress"
    )
    val shape = RoundedCornerShape(corner)
    val iconSize = if (featured) 56.dp else 44.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (featured) 140.dp else 144.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .background(tint.copy(alpha = if (featured) 0.16f else 0.07f))
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
            .then(if (tag.isBlank()) Modifier else Modifier.testTag(tag))
    ) {
        MarblePetalMark(
            color = tint.copy(alpha = 0.08f),
            size = if (featured) 132.dp else 96.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 0.dp)
                .graphicsLayer {
                    translationX = 28.dp.toPx()
                    translationY = (-24).dp.toPx()
                }
        )
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = glyph,
                        color = tint,
                        modifier = Modifier.size(if (featured) 28.dp else 22.dp),
                        weight = 1.1f
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.onSurface.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleChevron(size = 16.dp, tint = colors.onSurfaceVariant)
                }
            }
            Text(
                text = title,
                color = colors.onSurface,
                style = if (featured) {
                    MaterialTheme.typography.titleLargeEmphasized
                } else {
                    MaterialTheme.typography.titleMediumEmphasized
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
