package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.marbleng.app.model.TextScale

@Composable
fun ProvideMarbleTextScale(percent: Int, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scaled = remember(base.density, base.fontScale, percent) {
        Density(base.density, base.fontScale * TextScale.factor(percent))
    }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}
