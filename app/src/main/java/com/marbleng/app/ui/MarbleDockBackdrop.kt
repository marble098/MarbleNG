package com.marbleng.app.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import com.marbleng.app.model.LiquidLens

/**
 * MARBLE_HARMONY_V279 — a bounded, live backdrop for the phone dock.
 *
 * The page records its own drawing commands once; the dock replays that layer through a small
 * blurred, capsule-shaped window. No bitmap readback, screenshot permission or second rendering
 * of the page's composables is involved. The layer excludes the dock and every overlay above it,
 * so there is no feedback loop. While the dock is hidden for scrolling, recording stops.
 *
 * API 31 supplies RenderEffect. Older phones keep the same translucent material and optical rim,
 * without pretending to blur pixels their graphics stack cannot filter. The page itself is never
 * blurred: only the dock's private replay is, so text outside the glass stays crisp.
 */
@Stable
internal class MarbleDockBackdrop(
    private val layer: GraphicsLayer,
    private val blurredLayer: GraphicsLayer
) {
    var enabled by mutableStateOf(false)
    var pageBounds by mutableStateOf(Rect.Zero)
    private var recorded by mutableStateOf(false)

    fun capture(): Modifier = Modifier
        .onPlaced { pageBounds = it.boundsInRoot() }
        .drawWithContent {
            if (enabled && pageBounds.width > 0f && pageBounds.height > 0f) {
                layer.record { this@drawWithContent.drawContent() }
                recorded = true
                drawLayer(layer)
            } else {
                drawContent()
            }
        }

    fun draw(scope: DrawScope, dockBounds: Rect) {
        if (!enabled || !recorded || layer.isReleased || blurredLayer.isReleased || dockBounds.width <= 0f) return
        val radius = LiquidLens.BACKDROP_BLUR_RADIUS_PX
        // A separate display list owns the effect: changing it never changes the original page.
        // Record a little beyond the capsule so the blur has real neighbouring pixels at its rim.
        with(scope) {
            blurredLayer.record(
                size = androidx.compose.ui.unit.IntSize(
                    (dockBounds.width + radius * 2f).toInt().coerceAtLeast(1),
                    (dockBounds.height + radius * 2f).toInt().coerceAtLeast(1)
                )
            ) {
                translate(
                    left = radius + pageBounds.left - dockBounds.left,
                    top = radius + pageBounds.top - dockBounds.top
                ) { drawLayer(layer) }
            }
        }
        blurredLayer.topLeft = androidx.compose.ui.unit.IntOffset(-radius.toInt(), -radius.toInt())
        blurredLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
        scope.drawLayer(blurredLayer)
    }

    companion object {
        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

@Composable
internal fun rememberMarbleDockBackdrop(): MarbleDockBackdrop {
    val layer = rememberGraphicsLayer()
    val blurredLayer = rememberGraphicsLayer()
    return remember(layer, blurredLayer) { MarbleDockBackdrop(layer, blurredLayer) }
}
