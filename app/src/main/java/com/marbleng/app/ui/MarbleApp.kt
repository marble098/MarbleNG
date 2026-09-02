
package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import com.marbleng.app.AppRepository

@Composable
fun MarbleApp(repo: AppRepository) {
    AetherFlowTheme(themeId = repo.themeId.value) {
        MarbleAppShell(repo = repo)
    }
}
