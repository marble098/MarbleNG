package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ProxyProfile

@Composable
fun MarbleApp(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit
) {
    AetherFlowTheme(repo.settings.theme) {
        Aether2026App(repo = repo, onConnect = onConnect, onImportFile = onImportFile)
    }
}
