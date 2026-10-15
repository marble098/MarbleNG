package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ProxyProfile

@Composable
fun MarbleApp(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit = {}
) {
    ProvideMarbleLanguage(repo.settings.appLanguage) {
        ProvideMarbleTextScale(repo.settings.textScalePercent) {
            AetherFlowTheme(
                themeId = repo.settings.theme,
                fontId = repo.settings.fontFamily,

                outlineStyleId = repo.settings.darkOutlineStyle
            ) {
                Aether2026App(
                    repo = repo,
                    onConnect = onConnect,
                    onImportFile = onImportFile,
                    onContentScrollChanged = onContentScrollChanged
                )
            }
        }
    }
}
