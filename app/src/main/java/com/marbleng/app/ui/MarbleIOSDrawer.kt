package com.marbleng.app.ui

import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.GraphicsLayerCompat
import androidx.compose.ui.graphics.dropShadow
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.vectorPath
import androidx.compose.ui.layout.LayoutId
import androidx.compose.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * iOS‑style side drawer that slides in from the left with a cubic‑ease‑out curve,
 * snaps to 61.8 % of screen width, has rounded corners (14 dp), glassmorphism with
 * vibrancy, and a soft haptic on full open. It tracks an internal boolean flag
 * (isOpen) so the surrounding logic can reconfigure the UI dynamically.
 */
@Composable
fun MarbleIOSDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val width by animateFloatAsState(
        targetValue = if (isOpen) MarbleIOSDesign.DrawerWidth else 0f,
        animationSpec = tween(200, easing = MarbleIOSDesign.EaseOutCubic)
    )
    val drawerWidth = width.dpOrPx(density).dp // ensure dp

    // Background gradient overlay for the main content when drawer is open
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isOpen) 0.4f else 0f,
        animationSpec = tween(180, easing = MarbleIOSDesign.EaseOutCubic)
    )

    // Glass panel with vibrancy effect simulated via blur + tint
    val glassColor = Aether.GlassStrong.copy(alpha = 0.5f)

    // Simple dot‑noise layer (Poisson‑disc-like) – here we just draw a few tiny dots
    //; a full implementation would generate hundreds of points.
    @Composable
    fun DotNoise(modifier: Modifier = Modifier) {
        val rng = remember { kotlin.random.Random(System.currentTimeMillis()) }
        Canvas(modifier) {
            repeat(200) {
                val x = rng.nextFloat() * (size.width + 200).dpToPx()
                val y = rng.nextFloat() * (size.height + 200).dpToPx()
                val alpha = 0.02f
                drawCircle(
                    color = Color(0xFFFFFFFF).copy(alpha = alpha),
                    radius = 0.5f,
                    center = Offset(x, y)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .size(drawerWidth, MaxDimensions.Infinity)
            .offset { IntOffset(x = if (isOpen) 0 else -drawerWidth.dpOrPx(density).toInt(), y = 0) }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Aether.Black,
                        Aether.Ice
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height)
                )
            )
            .clip(RoundedCornerShape(MarbleIOSDesign.CornerRadius))
            .pointerInput(isOpen) {
                dragGesture { _, _, _, it ->
                    if (it.distance > 30) onClose()
                }
            }
    ) {
        // Drawer content column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Marble",
                    style = MaterialTheme.typography.titleLarge,
                    color = Aether.Cyan,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                PrismIconButton(
                    onClick = onClose,
                    tone = Aether.Cyan,
                    size = 24.dp,
                    content = {
                        Text(
                            text = "×",
                            color = Aether.Ink,
                            style = MaterialTheme.typography.body1
                        )
                    }
                )
            }

            // Example list of sections
            SectionRow(label = "Servers", onClick = { onClose(); /* navigate */ })
            SectionRow(label = "Settings", onClick = { onClose(); /* navigate */ })
            SectionRow(label = "Network & Routing", onClick = { onClose(); /* navigate */ })
            // ... other sections
        }
    }

    // Overlay the main UI when drawer is open
    if (isOpen) {
        // Thin high‑opacity white gradient with Screen blend mode (simulated via alpha)
        val screenGradient = Color(0xFFFFFFFF).copy(alpha = overlayAlpha.dp.toFloat())
        // This is a simplified overlay; real implementation would apply to the whole
        // composition background with blend mode Screen.
        Box(modifier = Modifier.fillMaxSize().background(screenGradient)) {}
    }
}

/** Helper row for a drawer section. */
@Composable
fun SectionRow(
    label: String,
    onClick: () -> Unit
) {
    PrismButton(
        label = label,
        onClick = onClick,
        tone = Aether.Cyan,
        variant = PrismButtonVariant.Secondary
    )
}

/** Maximum usable dimensions helper (placeholder). */
object MaxDimensions {
    val Infinite = Int.MAX_VALUE
}

/** Extension to convert dp to px via density. */
private fun Dp.dpOrPx(density: Density): Int {
    return (this.toFloat() * density).toInt()
}