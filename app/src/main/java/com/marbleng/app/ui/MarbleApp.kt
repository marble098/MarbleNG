package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ProxyProfile

/**
 * MarbleNG opens directly on the product surface. Android access prompts are intentionally owned by
 * the connection action in MainActivity, where each prompt can explain its purpose in context.
 */
@Composable
fun MarbleApp(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit = {}
) {
    AetherFlowTheme(
        themeId = repo.settings.theme,
        fontId = repo.settings.fontFamily
    ) {
        Aether2026App(
            repo = repo,
            onConnect = onConnect,
            onImportFile = onImportFile,
            onContentScrollChanged = onContentScrollChanged
        )
    }
}
