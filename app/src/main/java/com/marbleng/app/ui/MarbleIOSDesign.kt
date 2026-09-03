package com.marbleng.app.ui

// MARBLE_IOS_DESIGN_SYSTEM_V83 — a contained implementation of the twelve iOS design principles
// the product owner specified, kept in one file so it can be reasoned about and reused across
// surfaces. Where a principle needs a device capability (haptics, drag physics) the whole
// capability lives here rather than leaking gesture code into individual pages.

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

// ─── Principle 3 / 9 / 10 ──────────────────────────────────────────────────────────────────────
// A degree-three easing curve that starts gently and finishes with soft damping, so the drawer
// recreates a ballistic, throw-like movement with no sharp corner. The same curve drives the
// haptic-timed opening so the motion and the vibrancy layer never disagree.
val MarbleIOSCubicEase: CubicBezierEasing = CubicBezierEasing(
    a = 0.32f,
    b = 0.00f,
    c = 0.12f,
    d = 1.00f
)

// ─── Principle 4 ──────────────────────────────────────────────────────────────────────────────
// The cosmic floor: absolute black at the top sinking into a faint indigo-granite spectrum at the
// bottom, evoking a descent into the void.
val MarbleIOSCosmicGradient: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF000000),
        Color(0xFF070A12),
        Color(0xFF0E1630),
        Color(0xFF1B2136)
    )
)

// ─── Principle 2 ──────────────────────────────────────────────────────────────────────────────
// A San-Francisco-style family at a very thin optical weight (Thin). The display size is coupled to
// the dynamic type ramp (MarbleIOSTypeRamp) so the vertical rhythm of the drawer grid stays intact.
// SansSerif at Thin is the system face matching the iOS look without bundling a licensed font.
private val MarbleIOSFontFamily: FontFamily = FontFamily.SansSerif

// ─── Principle 8 ──────────────────────────────────────────────────────────────────────────────
// A geometric progression with ratio 1.07 between title, subtitle and body, giving breathing room
// without abrupt enlargement. base is the body size; every step multiplies by 1.07.
object MarbleIOSTypeRamp {
    private const val RATIO = 1.07f

    data class Ramp(
        val title: TextUnit,
        val subtitle: TextUnit,
        val body: TextUnit
    )

    fun of(base: TextUnit = 15.sp): Ramp = Ramp(
        title = base * (RATIO * RATIO),
        subtitle = base * RATIO,
        body = base
    )

    @Composable
    fun titleStyle(): TextStyle = MaterialTheme.typography.titleMedium.copy(
        fontFamily = MarbleIOSFontFamily,
        fontWeight = FontWeight.Thin,
        fontSize = of().title,
        letterSpacing = 0.1.sp
    )

    @Composable
    fun subtitleStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = MarbleIOSFontFamily,
        fontWeight = FontWeight.Thin,
        fontSize = of().subtitle,
        letterSpacing = 0.05.sp
    )

    @Composable
    fun bodyStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = MarbleIOSFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = of().body,
        letterSpacing = 0.02.sp
    )
}

// ─── Principle 5 ──────────────────────────────────────────────────────────────────────────────
// Hundreds of tiny sub-visual dots scattered by a random field across the whole canvas, emulating
// the static natural noise of a galactic background. The seed is stable so it only re-scatters when
// the caller asks, never per frame.
@Composable
fun MarbleIOSStarfield(
    modifier: Modifier = Modifier,
    seed: Int = 0x4D415242, // "MARB"
    pointCount: Int = 340
) {
    val rnd = remember(seed, pointCount) { Random(seed) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val dotRadius = (density * 0.85f).coerceAtLeast(0.75f)
        repeat(pointCount) { index ->
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = dotRadius * (0.5f + rnd.nextFloat() * 0.9f)
            val alpha = (0.10f + rnd.nextFloat() * 0.45f) * (1f - y / h).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r,
                center = Offset(x, y)
            )
            if (index % 7 == 0) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.6f),
                    radius = r * 0.4f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

// ─── Principles 4 + 5 ─────────────────────────────────────────────────────────────────────────
// The shared cosmic backdrop: the vertical gradient plus the scattered star field.
@Composable
fun MarbleIOSDeepSpace(
    modifier: Modifier = Modifier,
    seed: Int = 0x4D415242
) {
    Box(modifier = modifier.background(MarbleIOSCosmicGradient)) {
        MarbleIOSStarfield(Modifier.matchParentSize(), seed = seed)
    }
}

// ─── Principle 1 ──────────────────────────────────────────────────────────────────────────────
// The drawer open/closed state is a single Boolean living in the caller's logic layer. Every visual
// aspect of the drawer (offset, scrim opacity, haptics, content scale) derives from it, so a flip
// reconfigures the whole page arrangement dynamically rather than bolting on per-widget toggles.
@Composable
fun MarbleIOSDrawer(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "MarbleNG",
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (open) 1f else 0f) }
    val haptic = LocalHapticFeedback.current

    // ─── Principle 10 ─────────────────────────────────────────────────────────────────────────
    // Once the drawer has fully opened, a very soft, short tactile tick is delivered by the device
    // vibrancy engine. Bonded to the easing above so the haptic and the motion feel like one event.
    LaunchedEffect(open) {
        progress.animateTo(
            targetValue = if (open) 1f else 0f,
            animationSpec = tween(durationMillis = 320, easing = MarbleIOSCubicEase)
        )
        if (open) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // The system back gesture closes the drawer rather than the page, matching the iOS drawer.
    BackHandler(enabled = open) { onClose() }

    val widthFraction = 0.618f // ─── Principle 7: golden-ratio drawer width.
    val drawerShape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        // ─── Principle 12 ─────────────────────────────────────────────────────────────────────
        // A fading layer with dynamic opacity (zero → 40%) over the main content, with every motion
        // locked to one uniform rate so the eye is never caught by a mismatched rhythm.
        val scrimAlpha = progress.value * 0.40f
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(enabled = open && progress.value > 0.5f) { onClose() }
        )

        BoxWithConstraints(Modifier.matchParentSize()) {
            val drawerWidthPx = with(density) { (maxWidth * widthFraction).toPx() }
            val drawerWidth: Dp = maxWidth * widthFraction

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    // ─── Principle 9 ─────────────────────────────────────────────────────────
                    // Horizontal drag measures both finger position and instantaneous velocity. A
                    // fast fling past the threshold snaps the drawer shut, otherwise it springs back.
                    .offset { IntOffset((-(1f - progress.value) * drawerWidthPx).roundToInt(), 0) }
                    .graphicsLayer {
                        val p = progress.value
                        scaleX = 0.98f + 0.02f * p
                        scaleY = 0.98f + 0.02f * p
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            val next = (progress.value + (delta / drawerWidthPx) * 1.6f)
                                .coerceIn(0f, 1f)
                            scope.launch { progress.snapTo(next) }
                        },
                        onDragStopped = { velocity ->
                            scope.launch {
                                if (velocity > 900f || progress.value < 0.42f) {
                                    onClose()
                                } else {
                                    progress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            durationMillis = 240,
                                            easing = MarbleIOSCubicEase
                                        )
                                    )
                                }
                            }
                        }
                    )
                    .shadow(
                        elevation = 22.dp,
                        shape = drawerShape,
                        ambientColor = Color.Black.copy(alpha = 0.36f),
                        spotColor = Color.Black.copy(alpha = 0.44f)
                    )
                    .clip(drawerShape)
                    // ─── Principle 6 ─────────────────────────────────────────────────────────
                    // A semi-transparent glass layer: a high-bleed frosted field plus a vibrancy
                    // wash. The live backdrop is approximated with layered translucency because a
                    // true live-blur requires sampling the pixels behind the drawer, which Compose
                    // does not expose; the layered glass reads as the same frozen, deep surface.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF151A2E).copy(alpha = 0.94f),
                                Color(0xFF0A0E1C).copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.14f),
                        drawerShape
                    )
            ) {
                // ─── Principle 11 ─────────────────────────────────────────────────────────────
                // A thin white gradient of very high transparency over the whole drawer surface,
                // blended as Screen, simulating light reflecting off a dielectric glass surface.
                Canvas(Modifier.matchParentSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent
                            )
                        ),
                        blendMode = BlendMode.Screen
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(Modifier.height(22.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            title,
                            color = Color.White,
                            style = MarbleIOSTypeRamp.titleStyle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Menus",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MarbleIOSTypeRamp.bodyStyle()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    content()
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

// ─── Principle 2 ──────────────────────────────────────────────────────────────────────────────
// A single, typed drawer item. Title rides the Thin SF ramp; subtitle uses the 1.07 body step. The
// leading slot is supplied by the caller so this component stays free of page-specific icons.
@Composable
fun MarbleIOSDrawerItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            leading()
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                style = MarbleIOSTypeRamp.subtitleStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.5f),
                style = MarbleIOSTypeRamp.bodyStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Honors the platform animator-duration scale so ambient motion stays quiet when the user has
 * disabled animations. A small opt-in helper the app can call at the root.
 */
@Composable
fun MarbleIOSMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f) > 0f
    }
}

// Convenience accents for the cosmic surfaces.
val MarbleIOSInk: Color = Color(0xFFE8ECF7)
val MarbleIOSIndigo: Color = Color(0xFF3448B8)
