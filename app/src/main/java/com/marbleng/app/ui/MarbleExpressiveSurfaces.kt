@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleDefaultEffectsSpec
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.onSuccessContainer
import com.marbleng.app.ui.theme.onWarningContainer
import com.marbleng.app.ui.theme.successContainer
import com.marbleng.app.ui.theme.warningContainer

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 — Material 3 Expressive shapes, polygon morphing and contrast-paired
 * tonal surfaces shared across Home, Servers and Settings.
 *
 * Visual direction takes cues from Google's Material 3 Expressive system and Lumen Android 1.5.0
 * (without importing third-party source or assets): varied official `MaterialShapes` polygons,
 * spring-driven `Morph` transitions on press and state changes, asymmetric container silhouettes,
 * and contrast-safe tonal pairs that adapt to Light, Dark, True Black, Dynamic Color and all nine
 * Marble accents.
 */

internal enum class MarbleExpressiveMotif {
    FLOWER,
    CLOVER,
    SUNNY,
    COOKIE,
    FAN,
    DIAMOND,
    ARCH,
    BURST,
    GEM,
    PENTAGON,
    CIRCLE;

    companion object {
        fun forGlyph(glyph: MarbleGlyph): MarbleExpressiveMotif = when (glyph) {
            MarbleGlyph.PALETTE, MarbleGlyph.HOME -> FLOWER
            MarbleGlyph.PLUS, MarbleGlyph.COPY, MarbleGlyph.GLOBE, MarbleGlyph.LINK -> CLOVER
            MarbleGlyph.SPARK, MarbleGlyph.GAUGE, MarbleGlyph.RADAR -> SUNNY
            MarbleGlyph.TUNNEL, MarbleGlyph.DOWNLOAD, MarbleGlyph.SERVER -> FAN
            MarbleGlyph.QR, MarbleGlyph.REFRESH, MarbleGlyph.VAULT -> DIAMOND
            MarbleGlyph.ROUTE, MarbleGlyph.SHIELD, MarbleGlyph.FOLDER -> ARCH
            MarbleGlyph.TUNERS, MarbleGlyph.BELL, MarbleGlyph.INFO -> BURST
            MarbleGlyph.UPLOAD, MarbleGlyph.DOCK -> GEM
            MarbleGlyph.LAYERS, MarbleGlyph.FONT -> PENTAGON
            else -> COOKIE
        }
    }
}

internal fun marbleExpressivePolygon(motif: MarbleExpressiveMotif): RoundedPolygon = when (motif) {
    MarbleExpressiveMotif.FLOWER -> MaterialShapes.Flower
    MarbleExpressiveMotif.CLOVER -> MaterialShapes.Clover4Leaf
    MarbleExpressiveMotif.SUNNY -> MaterialShapes.Sunny
    MarbleExpressiveMotif.COOKIE -> MaterialShapes.Cookie9Sided
    MarbleExpressiveMotif.FAN -> MaterialShapes.Fan
    MarbleExpressiveMotif.DIAMOND -> MaterialShapes.PuffyDiamond
    MarbleExpressiveMotif.ARCH -> MaterialShapes.Arch
    MarbleExpressiveMotif.BURST -> MaterialShapes.SoftBurst
    MarbleExpressiveMotif.GEM -> MaterialShapes.Gem
    MarbleExpressiveMotif.PENTAGON -> MaterialShapes.Pentagon
    MarbleExpressiveMotif.CIRCLE -> MaterialShapes.Circle
}

@Composable
internal fun marbleExpressiveShape(motif: MarbleExpressiveMotif): Shape =
    marbleExpressivePolygon(motif).toShape()

private fun Morph.scaledPath(progress: Float, targetSize: Size): Path {
    val path = toPath(progress = progress.coerceIn(0f, 1f))
    if (targetSize.width <= 0f || targetSize.height <= 0f) return path
    val bounds = path.getBounds()
    if (bounds.width > 0f && bounds.height > 0f) {
        path.translate(Offset(-bounds.left, -bounds.top))
        val matrix = Matrix().apply {
            scale(targetSize.width / bounds.width, targetSize.height / bounds.height)
        }
        path.transform(matrix)
    }
    return path
}

/**
 * A [Shape] that morphs smoothly between two official Material 3 Expressive [RoundedPolygon]s.
 */
@Composable
internal fun marbleMorphingShape(
    from: MarbleExpressiveMotif,
    to: MarbleExpressiveMotif,
    progress: Float
): Shape {
    val morph = remember(from, to) {
        Morph(marbleExpressivePolygon(from), marbleExpressivePolygon(to))
    }
    val clamped = progress.coerceIn(0f, 1f)
    return remember(morph, clamped) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline = Outline.Generic(morph.scaledPath(clamped, size))
        }
    }
}

/**
 * Asymmetric Material 3 Expressive container shape: three generous rounded corners and one tighter
 * anchor corner, morphing on press with the theme's spatial spring and clamped against negative
 * overshoot.
 */
@Composable
internal fun marbleAsymmetricShape(
    prominent: Boolean = false,
    pressed: Boolean = false,
    flipped: Boolean = false
): Shape {
    val motion = marbleMotion()
    val mainTarget = when {
        pressed -> 16.dp
        prominent -> 34.dp
        else -> 28.dp
    }
    val anchorTarget = when {
        pressed -> 16.dp
        prominent -> 16.dp
        else -> 12.dp
    }
    val mainCorner by animateDpAsState(
        targetValue = mainTarget,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveMainCorner"
    )
    val anchorCorner by animateDpAsState(
        targetValue = anchorTarget,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveAnchorCorner"
    )
    val main = mainCorner.coerceAtLeast(0.dp)
    val anchor = anchorCorner.coerceAtLeast(0.dp)
    return if (flipped) {
        RoundedCornerShape(
            topStart = main,
            topEnd = anchor,
            bottomEnd = main,
            bottomStart = main
        )
    } else {
        RoundedCornerShape(
            topStart = main,
            topEnd = main,
            bottomEnd = main,
            bottomStart = anchor
        )
    }
}

/**
 * Expressive selection geometry: unselected controls rest as soft squircles and spring into full
 * stadium pills when selected.
 */
@Composable
internal fun marbleSelectionMorphShape(
    selected: Boolean,
    idleDp: Dp = 14.dp,
    selectedDp: Dp = 28.dp
): Shape {
    val motion = marbleMotion()
    val corner by animateDpAsState(
        targetValue = if (selected) selectedDp else idleDp,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveSelectionCorner"
    )
    return RoundedCornerShape(corner.coerceAtLeast(0.dp))
}

@Immutable
internal data class MarbleExpressivePair(
    val container: Color,
    val content: Color,
    val badge: Color,
    val onBadge: Color
)

@Immutable
internal data class MarbleExpressiveTones(
    val stage: MarbleExpressivePair,
    val sky: MarbleExpressivePair,
    val lavender: MarbleExpressivePair,
    val mint: MarbleExpressivePair,
    val amber: MarbleExpressivePair,
    val plum: MarbleExpressivePair
)

@Composable
internal fun marbleExpressiveTones(): MarbleExpressiveTones {
    val colors = MaterialTheme.colorScheme
    val flat = MarbleTheme.flat
    val base = if (flat) colors.surfaceContainerLow else colors.surfaceContainer
    return MarbleExpressiveTones(
        stage = MarbleExpressivePair(
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            badge = colors.primary,
            onBadge = colors.onPrimary
        ),
        sky = MarbleExpressivePair(
            container = colors.secondaryContainer.copy(alpha = 0.72f),
            content = colors.onSecondaryContainer,
            badge = colors.primary,
            onBadge = colors.onPrimary
        ),
        lavender = MarbleExpressivePair(
            container = colors.tertiaryContainer.copy(alpha = 0.72f),
            content = colors.onTertiaryContainer,
            badge = colors.tertiary,
            onBadge = colors.onTertiary
        ),
        mint = MarbleExpressivePair(
            container = colors.successContainer.copy(alpha = 0.76f),
            content = colors.onSuccessContainer,
            badge = MarbleTheme.status.success,
            onBadge = MarbleTheme.status.onSuccess
        ),
        amber = MarbleExpressivePair(
            container = colors.warningContainer.copy(alpha = 0.76f),
            content = colors.onWarningContainer,
            badge = MarbleTheme.status.warning,
            onBadge = MarbleTheme.status.onWarning
        ),
        plum = MarbleExpressivePair(
            container = base,
            content = colors.onSurface,
            badge = colors.secondary,
            onBadge = colors.onSecondary
        )
    )
}

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
 * Cached morphing Material 3 Expressive polygon watermark. Draws only inside `onDrawBehind` so
 * morph progress reads never recompose the surrounding layout.
 */
@Composable
internal fun MarbleMorphingMark(
    motif: MarbleExpressiveMotif,
    color: Color,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    targetMotif: MarbleExpressiveMotif = MarbleExpressiveMotif.CIRCLE
) {
    val morph = remember(motif, targetMotif) {
        Morph(marbleExpressivePolygon(motif), marbleExpressivePolygon(targetMotif))
    }
    Box(
        modifier = modifier.drawWithCache {
            var cachedProgress = Float.NaN
            var cachedSize = Size.Zero
            var cachedPath: Path? = null
            onDrawBehind {
                val p = progress().coerceIn(0f, 1f)
                if (p != cachedProgress || size != cachedSize) {
                    cachedPath = morph.scaledPath(p, size)
                    cachedProgress = p
                    cachedSize = size
                }
                cachedPath?.let { drawPath(it, color) }
            }
        }
    )
}

/**
 * Decorative Material 3 Expressive polygon mark. Supports any [MarbleExpressiveMotif] and optional
 * morphing to [targetMotif]. Respects [MarbleMotionStyle.loops] so calm/reduced-motion users get a
 * crisp resting polygon.
 */
@Composable
internal fun MarblePetalMark(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animated: Boolean = true,
    motif: MarbleExpressiveMotif = MarbleExpressiveMotif.COOKIE,
    targetMotif: MarbleExpressiveMotif = MarbleExpressiveMotif.FLOWER,
    morphProgress: Float? = null
) {
    val motion = marbleMotion()
    val floating = animated && motion.loops
    val transition = rememberInfiniteTransition(label = "petalBloom")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing)
        ),
        label = "petalTurn"
    )
    val bloom by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "petalBloom"
    )
    val effectiveMorph = morphProgress ?: if (floating) bloom * 0.45f else 0f
    val scale = if (floating) 0.95f + 0.08f * bloom else 1f
    MarbleMorphingMark(
        motif = motif,
        targetMotif = targetMotif,
        color = color,
        progress = { effectiveMorph },
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = if (floating) turn else 0f
                scaleX = scale
                scaleY = scale
            }
    )
}

/**
 * Expressive hero bloom for inner Settings pages: replaces the old wireframe circle/dot with an
 * authentic morphing Material 3 Expressive polygon (`SoftBurst` ↔ `Flower`).
 */
@Composable
internal fun MarbleOrbitLens(
    tint: Color,
    modifier: Modifier = Modifier,
    periodMs: Int = 7_000,
    radiusFraction: Float = 0.42f,
    motif: MarbleExpressiveMotif = MarbleExpressiveMotif.BURST
) {
    val motion = marbleMotion()
    if (!motion.loops) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    val s = (radiusFraction * 2f).coerceIn(0.5f, 1f)
                    scaleX = s
                    scaleY = s
                }
                .clip(marbleExpressiveShape(motif))
                .background(tint.copy(alpha = 0.12f))
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "orbitLens")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs * 3, easing = LinearEasing)
        ),
        label = "orbitLensAngle"
    )
    val morph by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbitLensMorph"
    )
    val scaleFactor = (radiusFraction * 2.15f).coerceIn(0.6f, 1.05f)
    MarbleMorphingMark(
        motif = motif,
        targetMotif = MarbleExpressiveMotif.FLOWER,
        color = tint.copy(alpha = 0.14f),
        progress = { morph },
        modifier = modifier.graphicsLayer {
            rotationZ = angle
            scaleX = scaleFactor
            scaleY = scaleFactor
        }
    )
}

/**
 * Header/inline action button clipped to an official Material 3 Expressive polygon that morphs
 * toward a circle and springs on press while keeping a 48dp+ accessible target.
 */
@Composable
internal fun MarbleExpressiveIconButton(
    glyph: MarbleGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    motif: MarbleExpressiveMotif = MarbleExpressiveMotif.forGlyph(glyph),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    prominent: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String = ""
) {
    val haptics = LocalHapticFeedback.current
    val motion = marbleMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val morphProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveBtnMorph"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveBtnScale"
    )
    val spin by animateFloatAsState(
        targetValue = if (pressed) -16f else 0f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveBtnSpin"
    )
    val buttonSize = if (prominent) 52.dp else 48.dp
    val shape = marbleMorphingShape(
        from = motif,
        to = MarbleExpressiveMotif.CIRCLE,
        progress = morphProgress
    )
    val described = if (contentDescription.isNotBlank()) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Box(
        modifier = described
            .size(buttonSize)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                rotationZ = spin
            }
            .clip(shape)
            .background(containerColor.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        MarbleGlyphIcon(
            glyph = glyph,
            color = if (enabled) contentColor else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(if (prominent) 22.dp else 20.dp)
                .graphicsLayer { rotationZ = -spin },
            weight = if (prominent) 1.15f else 1.05f
        )
    }
}

/**
 * Expressive navigation tile used on Home quick actions and the consolidated Settings Bento hub.
 * Each tile carries its own [MarbleExpressiveMotif] polygon badge, a morphing background watermark,
 * asymmetric spring corners, optional catalogue index, and optional consolidated sub-topic pills.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    tag: String = "",
    motif: MarbleExpressiveMotif = MarbleExpressiveMotif.forGlyph(glyph),
    indexBadge: String = "",
    topicPills: List<String> = emptyList(),
    containerOverride: Color? = null,
    contentOverride: Color? = null
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val motion = marbleMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) MarbleSpec.PRESSED_SCALE else 1f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveTilePress"
    )
    val morphProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveTileMorph"
    )
    val badgeSpin by animateFloatAsState(
        targetValue = if (pressed) -22f else 8f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "expressiveTileBadgeSpin"
    )
    val baseContainer = containerOverride ?: colors.surfaceContainerLow
    val containerTone by animateColorAsState(
        targetValue = baseContainer,
        animationSpec = marbleDefaultEffectsSpec(),
        label = "expressiveTileContainer"
    )
    val ink = contentOverride ?: colors.onSurface
    val subInk = if (contentOverride != null) {
        contentOverride.copy(alpha = 0.78f)
    } else {
        colors.onSurfaceVariant
    }

    val shape = marbleAsymmetricShape(
        prominent = featured,
        pressed = pressed
    )
    val iconSize = if (featured) 54.dp else 44.dp

    val ambientTurn = if (enabled && motion.loops) {
        val transition = rememberInfiniteTransition(label = "tileMotifTurn")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 26_000 + motif.ordinal * 2_200,
                    easing = LinearEasing
                )
            ),
            label = "tileMotifAngle"
        )
        angle
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (featured) 148.dp else 144.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerTone)
            .then(
                if (containerOverride == null) {
                    Modifier.background(tint.copy(alpha = if (featured) 0.14f else 0.08f))
                } else {
                    Modifier
                }
            )
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
        MarbleMorphingMark(
            motif = motif,
            targetMotif = MarbleExpressiveMotif.CIRCLE,
            color = (if (contentOverride != null) contentOverride else tint).copy(alpha = 0.10f),
            progress = { morphProgress },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(if (featured) 152.dp else 112.dp)
                .graphicsLayer {
                    translationX = 28.dp.toPx()
                    translationY = 26.dp.toPx()
                    rotationZ = badgeSpin + ambientTurn * if (motif.ordinal % 2 == 0) 1f else -1f
                }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (featured) 18.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer { rotationZ = badgeSpin }
                        .clip(marbleExpressiveShape(motif))
                        .background(tint.copy(alpha = if (containerOverride != null) 0.22f else 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = glyph,
                        color = tint,
                        modifier = Modifier
                            .size(if (featured) 26.dp else 21.dp)
                            .graphicsLayer { rotationZ = -badgeSpin },
                        weight = 1.12f
                    )
                }
                Spacer(Modifier.weight(1f))
                if (indexBadge.isNotBlank()) {
                    Text(
                        text = indexBadge,
                        color = subInk.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleChevron(size = 15.dp, tint = subInk)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = ink,
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
                        color = subInk,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (topicPills.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    topicPills.forEach { pill ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ink.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = pill,
                                color = ink.copy(alpha = 0.86f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
