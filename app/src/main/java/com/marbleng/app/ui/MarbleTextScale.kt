package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.marbleng.app.model.TextScale

// MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — the provider is now installed at the activity root
// (so root-level dialogs get it) and again inside MarbleApp (its historical home). Scaling the
// density twice would square the user's text scale, so the second installation is a no-op.
private val LocalMarbleTextScaleApplied = staticCompositionLocalOf { false }

@Composable
fun ProvideMarbleTextScale(percent: Int, content: @Composable () -> Unit) {
    if (LocalMarbleTextScaleApplied.current) {
        content()
        return
    }
    val base = LocalDensity.current
    val scaled = remember(base.density, base.fontScale, percent) {
        Density(base.density, base.fontScale * TextScale.factor(percent))
    }
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalMarbleTextScaleApplied provides true,
        content = content
    )
}
