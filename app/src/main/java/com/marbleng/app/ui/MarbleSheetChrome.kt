package com.marbleng.app.ui

// MARBLE_AURORA_SURFACE_V219 — the grabber a sheet is opened by.
//
// A modal sheet is the only place in the product where a gesture is *taught* rather than guessed:
// the 42 × 4 dp grey capsule at the top of a sheet is Android 2014's way of saying "this moves", and
// on a page whose whole language is lit material it read as a leftover. This grabber is still one
// object and still 4 dp of travel, but it is a lit object: a tone-cast shadow under it, a lit top
// stop, a quiet bottom stop, and a hairline that catches the same light as every card rim in the
// product. It is deliberately *not* animated — the sheet's own motion teaches the gesture.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * MARBLE_AURORA_SURFACE_V219 — the sheet grabber.
 *
 * [tone] defaults to the live palette's cyan. The grabber is 46 × 5 dp: wide enough to read as a
 * handle rather than a full stop, thick enough to keep a 4 dp visual weight at 3× density without
 * becoming a bar in its own right.
 */
@Composable
internal fun MarbleSheetGrabber(
    modifier: Modifier = Modifier,
    tone: Color? = null
) {
    val accent = tone ?: Aether.Cyan
    val dark = homeCloudDark()
    val shape = RoundedCornerShape(999.dp)
    val lit = lerp(accent, Color.White, if (dark) .58f else .72f)
    val rest = lerp(accent, Color.Black, if (dark) .10f else .04f)

    Column(
        modifier = modifier.padding(top = 10.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 5.dp)
                .marbleShadow(
                    elevation = 5.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = accent.copy(alpha = if (dark) .45f else .30f),
                    spotColor = accent.copy(alpha = if (dark) .60f else .40f)
                )
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lit.copy(alpha = .92f),
                            accent.copy(alpha = .74f),
                            rest.copy(alpha = .70f)
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .50f), Color.White.copy(alpha = 0f)),
                        startY = 0f,
                        endY = 6f
                    )
                )
        )
    }
}
