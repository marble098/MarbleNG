package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.marbleng.app.model.TextScale

/**
 * MARBLE_TEXT_SCALE_V215 — the whole app's text size, applied at the root.
 *
 * Text is measured in `sp`, and `sp` is `dp` times the density's font scale. Replacing the font
 * scale of the [LocalDensity] under the app therefore resizes every piece of text in every screen
 * and leaves every `dp` dimension alone, so the layout keeps its geometry: only the words grow or
 * shrink. The system's own font scale is kept and multiplied, so a user who already enlarged text in
 * Android is not shrunk back by the app.
 */
@Composable
fun ProvideMarbleTextScale(percent: Int, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scaled = remember(base.density, base.fontScale, percent) {
        Density(base.density, base.fontScale * TextScale.factor(percent))
    }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}
