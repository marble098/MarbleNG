package com.marbleng.app.ui

// MARBLE_EXPRESSIVE_MOTION_V186

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object MarbleExpressiveMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    const val Short1 = 50
    const val Short2 = 100
    const val Short3 = 150
    const val Short4 = 200
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium3 = 350
    const val Medium4 = 400
    const val Long1 = 450
    const val Long2 = 500
    const val Long3 = 550
    const val Long4 = 600
    const val ExtraLong1 = 700
    const val ExtraLong2 = 800
    const val ExtraLong3 = 900
    const val ExtraLong4 = 1_000

    const val StaggerStepMs = 45L

    const val EntranceWindowMs = 700L
}

internal object MarbleExpressiveSpecs {
    val PressInFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .72f,
        stiffness = 1_100f
    )

    // MARBLE_SMOOTH_MOTION_V220 — releases settle instead of bouncing: one soft overshoot at most,
    // so a fast scroll of tappable rows never shimmers.
    val SpringReleaseFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .58f,
        stiffness = 620f
    )

    val SpringPopFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .50f,
        stiffness = 700f
    )

    val ProgressSettleFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .82f,
        stiffness = 260f
    )

    val SpringReleaseDp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = .46f,
        stiffness = 560f
    )

    val MorphPressDp: FiniteAnimationSpec<Dp> = tween(
        durationMillis = MarbleExpressiveMotion.Short4,
        easing = MarbleExpressiveMotion.Emphasized
    )

    val MorphReleaseDp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = .48f,
        stiffness = 620f
    )

    val EntranceRiseDp: FiniteAnimationSpec<Dp> = tween(
        durationMillis = MarbleExpressiveMotion.Long2,
        easing = MarbleExpressiveMotion.EmphasizedDecelerate
    )

    val EntranceFadeFloat: FiniteAnimationSpec<Float> = tween(
        durationMillis = MarbleExpressiveMotion.Medium3,
        easing = MarbleExpressiveMotion.EmphasizedDecelerate
    )

    val EntranceRiseSpatial: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = MarbleExpressiveMotion.Long2,
        easing = MarbleExpressiveMotion.EmphasizedDecelerate
    )

    val RollInSpatial: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = MarbleExpressiveMotion.Medium3,
        easing = MarbleExpressiveMotion.EmphasizedDecelerate
    )

    val RollOutSpatial: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = MarbleExpressiveMotion.Medium2,
        easing = MarbleExpressiveMotion.EmphasizedAccelerate
    )

    val EmphasizedColor: FiniteAnimationSpec<Color> = tween(
        durationMillis = MarbleExpressiveMotion.Medium4,
        easing = MarbleExpressiveMotion.Emphasized
    )

    val EmphasizedFloat: FiniteAnimationSpec<Float> = tween(
        durationMillis = MarbleExpressiveMotion.Long1,
        easing = MarbleExpressiveMotion.Emphasized
    )

    val WaveSpringFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = .58f,
        stiffness = 320f
    )
}

internal object ExpressiveMath {
    const val STAGGER_STEP_MS: Long = 45L

    const val STAGGER_MAX_INDEX: Int = 6

    fun staggerDelayMs(index: Int): Long {
        if (index <= 0) return 0L
        return index.coerceAtMost(STAGGER_MAX_INDEX) * STAGGER_STEP_MS
    }

    fun wrap01(phase: Float): Float = ((phase % 1f) + 1f) % 1f

    fun wave(phase: Float): Float {
        val wrapped = wrap01(phase)
        return .5f - .5f * cos(wrapped * 2f * PI.toFloat())
    }

    fun wavyValue(phase: Float, min: Float, max: Float): Float =
        min + (max - min) * wave(phase)

    fun morph(from: Float, to: Float, t: Float): Float =
        from + (to - from) * t.coerceIn(0f, 1f)

    fun depthScale(
        offsetPages: Float,
        restScale: Float = 1f,
        minScale: Float = .92f
    ): Float {
        val magnitude = abs(offsetPages).coerceAtMost(1f)
        return restScale + (minScale - restScale) * magnitude
    }

    fun depthAlpha(
        offsetPages: Float,
        restAlpha: Float = 1f,
        minAlpha: Float = .5f
    ): Float {
        val magnitude = abs(offsetPages).coerceAtMost(1f)
        return restAlpha + (minAlpha - restAlpha) * (magnitude * magnitude)
    }

    fun arcSweep(phase: Float, minSweep: Float = 16f, maxSweep: Float = 68f): Float =
        wavyValue(phase, minSweep, maxSweep)

    fun degreesToRadians(degrees: Float): Float = degrees * (PI.toFloat() / 180f)

    fun pointOnCircle(cx: Float, cy: Float, radius: Float, angleDegrees: Float): Offset {
        val radians = degreesToRadians(angleDegrees)
        return Offset(cx + cos(radians) * radius, cy + sin(radians) * radius)
    }
}

@Composable
internal fun MarbleExpressiveCircularIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp,
    arcCount: Int = 4
) {
    val resolved = if (color == Color.Unspecified) Aether.Cyan else color
    val resolvedTrack = if (trackColor == Color.Unspecified) resolved.copy(alpha = .14f) else trackColor
    val motion = MarbleMotion.current
    val motionOn = motion.motionEnabled

    val settled = remember { Animatable((progress ?: 0f).coerceIn(0f, 1f)) }
    LaunchedEffect(progress) {
        val target = progress?.coerceIn(0f, 1f)
        if (target != null) {
            settled.animateTo(target, MarbleExpressiveSpecs.ProgressSettleFloat)
        }
    }

    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val box = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f))
        val topLeft = Offset(inset, inset)

        drawArc(
            color = resolvedTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = stroke)
        )

        if (progress == null) {
            if (!motionOn) {
                drawArc(
                    color = resolved,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            } else {
                val rotation = motion.loop(1_650) * 360f
                val wavePhase = motion.loop(1_100)
                val count = arcCount.coerceAtLeast(1)
                repeat(count) { index ->

                    val arcPhase = ExpressiveMath.wrap01(wavePhase + index.toFloat() / count)
                    val sweep = ExpressiveMath.arcSweep(arcPhase)

                    val wobble = ExpressiveMath.wavyValue(ExpressiveMath.wrap01(arcPhase * 2f), -7f, 7f)
                    val start = rotation + index * (360f / count) + wobble

                    val alpha = .50f + .50f * ((sweep - 16f) / 52f).coerceIn(0f, 1f)
                    drawArc(
                        color = resolved.copy(alpha = alpha),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = box,
                        style = Stroke(
                            width = stroke * (.78f + .44f * (sweep / 68f)),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        } else {
            val sweep = 360f * settled.value
            if (sweep > .5f) {
                drawArc(
                    color = resolved,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                val head = ExpressiveMath.pointOnCircle(
                    cx = topLeft.x + box.width / 2f,
                    cy = topLeft.y + box.height / 2f,
                    radius = box.minDimension / 2f,
                    angleDegrees = sweep - 90f
                )
                drawCircle(color = resolved.copy(alpha = .22f), radius = stroke * 1.6f, center = head)
                drawCircle(color = resolved, radius = stroke * .62f, center = head)
            }
        }
    }
}

@Composable
internal fun MarbleExpressiveLinearIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    height: Dp = 5.dp
) {
    val resolved = if (color == Color.Unspecified) Aether.Cyan else color
    val resolvedTrack = if (trackColor == Color.Unspecified) resolved.copy(alpha = .13f) else trackColor
    val motion = MarbleMotion.current
    val motionOn = motion.motionEnabled

    val settled = remember { Animatable((progress ?: 0f).coerceIn(0f, 1f)) }
    LaunchedEffect(progress) {
        val target = progress?.coerceIn(0f, 1f)
        if (target != null) {
            settled.animateTo(target, MarbleExpressiveSpecs.ProgressSettleFloat)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val corner = CornerRadius(size.height / 2f)

        drawRoundRect(color = resolvedTrack, size = size, cornerRadius = corner)

        if (progress == null) {
            if (!motionOn) {
                drawRoundRect(
                    color = resolved,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width * .35f, size.height),
                    cornerRadius = corner
                )
            } else {
                val travel = MarbleExpressiveMotion.Emphasized.transform(motion.loop(1_750))
                val center = (travel * 1.5f - .25f) * size.width
                val blobWidth = ExpressiveMath.wavyValue(motion.loop(900), .12f, .34f) * size.width
                val left = center - blobWidth / 2f
                val right = center + blobWidth / 2f
                val drawnLeft = left.coerceIn(-blobWidth, size.width)
                val drawnRight = right.coerceIn(0f, size.width + blobWidth)
                if (drawnRight > drawnLeft) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                resolved.copy(alpha = .30f),
                                resolved,
                                resolved.copy(alpha = .30f)
                            ),
                            startX = drawnLeft,
                            endX = drawnRight
                        ),
                        topLeft = Offset(drawnLeft, 0f),
                        size = Size(drawnRight - drawnLeft, size.height),
                        cornerRadius = corner
                    )
                }
            }
        } else {
            val fillWidth = size.width * settled.value
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = resolved,
                    topLeft = Offset(0f, 0f),
                    size = Size(fillWidth, size.height),
                    cornerRadius = corner
                )

                if (motionOn && settled.value < .995f) {
                    val shimmerX = motion.loop(1_100) * (fillWidth + size.width * .36f) - size.width * .18f
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = .26f),
                                Color.Transparent
                            ),
                            startX = shimmerX - fillWidth * .18f,
                            endX = shimmerX + fillWidth * .18f
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(fillWidth, size.height),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

@Composable
internal fun MarbleWavySegmentedProgress(
    steps: Int,
    completed: Int,
    active: Boolean,
    tone: Color,
    modifier: Modifier = Modifier,
    segmentHeight: Dp = 6.dp
) {
    val motion = MarbleMotion.current
    val track = Aether.GlassBorderSoft
    Canvas(
        modifier = modifier.height(segmentHeight)
    ) {
        if (steps <= 0) return@Canvas
        val gap = 4.dp.toPx()
        val segmentWidth = ((size.width - gap * (steps - 1)) / steps).coerceAtLeast(1f)
        val corner = CornerRadius(size.height / 2f)
        repeat(steps) { index ->
            val left = index * (segmentWidth + gap)
            val filled = index < completed
            val breathing = active && index == completed.coerceIn(0, steps - 1)
            val color = when {
                filled -> tone
                breathing -> tone.copy(
                    alpha = if (motion.motionEnabled) {
                        ExpressiveMath.wavyValue(motion.loop(1_200), .28f, .85f)
                    } else {
                        .55f
                    }
                )
                else -> track.copy(alpha = .55f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = corner
            )
        }
    }
}

fun Modifier.marbleStaggerIn(
    index: Int,
    enabled: Boolean = true,
    riseStart: Dp = 18.dp,
    scaleStart: Float = .96f
): Modifier = composed {
    val motion = MarbleMotion.current
    val animates = motion.animates()
    var shown by remember { mutableStateOf(!enabled || !animates) }
    LaunchedEffect(enabled, animates) {
        if (!enabled || !animates) {
            shown = true
            return@LaunchedEffect
        }
        delay(motion.entranceDelayFor(index))
        shown = true
    }
    val rise by animateDpAsState(
        targetValue = if (shown) 0.dp else riseStart,
        animationSpec = if (animates) MarbleExpressiveSpecs.EntranceRiseDp else snap(),
        label = "expressive-stagger-rise"
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (animates) MarbleExpressiveSpecs.EntranceFadeFloat else snap(),
        label = "expressive-stagger-fade"
    )
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else scaleStart,
        animationSpec = if (animates) MarbleExpressiveSpecs.SpringReleaseFloat else snap(),
        label = "expressive-stagger-scale"
    )
    if (!animates) {
        this
    } else {
        this.graphicsLayer {
            alpha = fade
            translationY = rise.toPx()
            scaleX = scale
            scaleY = scale
        }
    }
}

fun Modifier.marbleSpringIn(
    enabled: Boolean = true,
    riseStart: Dp = 22.dp,
    scaleStart: Float = .94f
): Modifier = marbleStaggerIn(index = 0, enabled = enabled, riseStart = riseStart, scaleStart = scaleStart)

@Composable
internal fun rememberMarbleEntranceWindow(durationMs: Long = MarbleExpressiveMotion.EntranceWindowMs): () -> Boolean {
    var armed by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(durationMs)
        armed = false
    }
    return { armed }
}

fun Modifier.expressiveClickable(
    enabled: Boolean = true,
    role: Role? = null,
    pressScale: Float = .955f,
    boundedShape: Shape = RoundedCornerShape(22.dp),
    showIndication: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressScale else 1f,
        animationSpec = if (pressed) MarbleExpressiveSpecs.PressInFloat else MarbleExpressiveSpecs.SpringReleaseFloat,
        label = "expressive-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (enabled && pressed) 1.4f else 0f,
        animationSpec = if (pressed) MarbleExpressiveSpecs.PressInFloat else MarbleExpressiveSpecs.SpringReleaseFloat,
        label = "expressive-press-lift"
    )
    val indication = if (showIndication) LocalIndication.current else null

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = lift
            shape = boundedShape
            clip = true
        }
        .clickable(
            interactionSource = source,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}

fun Modifier.marblePopWhen(trigger: Any?, peak: Float = 1.16f): Modifier = composed {
    val pop = remember { Animatable(1f) }
    var firstPass by remember { mutableStateOf(true) }

    val animates = MarbleMotion.current.animates()
    LaunchedEffect(trigger, animates) {
        if (!animates) {
            pop.snapTo(1f)
            return@LaunchedEffect
        }
        if (firstPass) {
            firstPass = false
            return@LaunchedEffect
        }
        pop.snapTo(peak)
        pop.animateTo(1f, MarbleExpressiveSpecs.SpringPopFloat)
    }
    this.graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
    }
}

@Composable
internal fun rememberExpressiveMorphShape(
    interactionSource: MutableInteractionSource,
    restRadius: Dp,
    pressedRadius: Dp
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = if (pressed) pressedRadius else restRadius,
        animationSpec = if (pressed) MarbleExpressiveSpecs.MorphPressDp else MarbleExpressiveSpecs.MorphReleaseDp,
        label = "expressive-shape-morph"
    )
    return RoundedCornerShape(radius.coerceAtLeast(0.dp))
}

internal fun Modifier.marblePageDepth(offsetProvider: () -> Float): Modifier = this.graphicsLayer {
    val offset = offsetProvider()
    val scale = ExpressiveMath.depthScale(offset)
    scaleX = scale
    scaleY = scale
    alpha = ExpressiveMath.depthAlpha(offset)
}

internal fun AnimatedContentTransitionScope<*>.expressiveContainerTransform(
    forward: Boolean = true,
    slideDivisor: Int = 12
): ContentTransform = (
    fadeIn(
        tween(
            durationMillis = MarbleExpressiveMotion.Medium2,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        )
    ) + scaleIn(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Long1,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        ),
        initialScale = if (forward) .94f else 1.04f
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Long1,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        )
    ) { height -> if (forward) height / slideDivisor else -height / slideDivisor }
    ) togetherWith (
    fadeOut(
        tween(
            durationMillis = MarbleExpressiveMotion.Short4,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        )
    ) + scaleOut(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Medium3,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        ),
        targetScale = if (forward) 1.03f else .97f
    ) + slideOutVertically(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Medium3,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        )
    ) { height -> if (forward) -height / (slideDivisor * 2) else height / (slideDivisor * 2) }
    )

internal fun AnimatedContentTransitionScope<*>.expressiveSharedAxisX(
    direction: Int,
    slideDivisor: Int = 7
): ContentTransform = (
    fadeIn(
        tween(
            durationMillis = MarbleExpressiveMotion.Medium1,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        )
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Long1,
            easing = MarbleExpressiveMotion.EmphasizedDecelerate
        )
    ) { width -> width / slideDivisor * direction }
    ) togetherWith (
    fadeOut(
        tween(
            durationMillis = MarbleExpressiveMotion.Short4,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        )
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = MarbleExpressiveMotion.Medium3,
            easing = MarbleExpressiveMotion.EmphasizedAccelerate
        )
    ) { width -> -width / slideDivisor * direction }
    )

internal fun expressiveFadeThrough(): ContentTransform =
    fadeIn(
        tween(
            durationMillis = MarbleExpressiveMotion.Medium1,
            delayMillis = 90,
            easing = LinearEasing
        )
    ) togetherWith fadeOut(
        tween(
            durationMillis = 90,
            easing = LinearEasing
        )
    )

internal fun expressiveExpandVertically(): EnterTransition =
    fadeIn(tween(durationMillis = 150, delayMillis = 60, easing = LinearEasing)) +
        expandVertically(MarbleMotionSpecs.Layout)

internal fun expressiveCollapseVertically(): ExitTransition =
    fadeOut(tween(durationMillis = 90, easing = LinearEasing)) +
        shrinkVertically(MarbleMotionSpecs.Layout)

@Composable
internal fun MarbleExpressiveValueText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    maxLines: Int = 1
) {
    val resolved = if (color == Color.Unspecified) Aether.Ink else color
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Short3,
                        easing = MarbleExpressiveMotion.EmphasizedDecelerate
                    )
                ) + slideInVertically(MarbleExpressiveSpecs.RollInSpatial) { height -> height / 2 }
                ) togetherWith (
                fadeOut(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Short2,
                        easing = MarbleExpressiveMotion.EmphasizedAccelerate
                    )
                ) + slideOutVertically(MarbleExpressiveSpecs.RollOutSpatial) { height -> -height / 2 }
                )
        },
        label = "expressive-value-roll"
    ) { current ->
        Text(
            current,
            color = resolved,
            style = style,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            maxLines = maxLines,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
internal fun MarbleStatePulseDot(
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 10.dp,
    pulsing: Boolean = true
) {
    val motion = MarbleMotion.current
    Canvas(
        modifier = modifier.size(diameter)
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        if (pulsing && motion.motionEnabled) {
            val phase = motion.loop(2_600)
            drawCircle(
                color = color.copy(alpha = .32f * (1f - phase)),
                radius = radius * (.55f + .45f * phase),
                center = center
            )
            val offsetPhase = ExpressiveMath.wrap01(phase + .5f)
            drawCircle(
                color = color.copy(alpha = .20f * (1f - offsetPhase)),
                radius = radius * (.55f + .45f * offsetPhase),
                center = center
            )
        }

        drawCircle(color = color.copy(alpha = .20f), radius = radius, center = center)
        drawCircle(color = color, radius = radius * .52f, center = center)
    }
}

@Composable
internal fun MarbleExpressiveGlyphSwap(
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = key,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Medium1,
                        easing = MarbleExpressiveMotion.EmphasizedDecelerate
                    )
                ) + scaleIn(
                    animationSpec = MarbleExpressiveSpecs.WaveSpringFloat,
                    initialScale = .72f
                )
                ) togetherWith (
                fadeOut(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Short4,
                        easing = MarbleExpressiveMotion.EmphasizedAccelerate
                    )
                ) + scaleOut(
                    animationSpec = tween(
                        durationMillis = MarbleExpressiveMotion.Short4,
                        easing = MarbleExpressiveMotion.EmphasizedAccelerate
                    ),
                    targetScale = .72f
                )
                )
        },
        label = "expressive-glyph-swap"
    ) {
        content()
    }
}
