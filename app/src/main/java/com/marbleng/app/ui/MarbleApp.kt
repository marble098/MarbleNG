package com.marbleng.app.ui

// MARBLE_FIRST_RUN_PERMISSIONS_V41

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ProxyProfile

private const val ONBOARDING_PREFERENCES = "marble_onboarding"
private const val ONBOARDING_COMPLETE = "permissions_complete_v1"

@Composable
fun MarbleApp(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            ONBOARDING_PREFERENCES,
            android.content.Context.MODE_PRIVATE
        )
    }
    var onboardingComplete by rememberSaveable {
        mutableStateOf(preferences.getBoolean(ONBOARDING_COMPLETE, false))
    }

    AetherFlowTheme(repo.settings.theme) {
        if (onboardingComplete) {
            Aether2026App(repo = repo, onConnect = onConnect, onImportFile = onImportFile)
        } else {
            MarblePermissionOnboarding(
                onComplete = {
                    preferences.edit().putBoolean(ONBOARDING_COMPLETE, true).apply()
                    onboardingComplete = true
                }
            )
        }
    }
}
