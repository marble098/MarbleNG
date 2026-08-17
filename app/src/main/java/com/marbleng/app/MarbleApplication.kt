package com.marbleng.app

// MARBLE_ULTIMATE_DIAGNOSTICS_BOOT_V15

import android.app.Application
import com.marbleng.app.core.RuntimeDiagnostics
import com.marbleng.app.core.XrayManager

class MarbleApplication : Application() {
    lateinit var xray: XrayManager
    lateinit var repo: AppRepository

    override fun onCreate() {
        super.onCreate()

        RuntimeDiagnostics.install(this)
        val diagnostics = RuntimeDiagnostics(this)
        diagnostics.event("APP", "process-create", "system" to diagnostics.systemSnapshot())

        xray = XrayManager(this)
        repo = AppRepository(this, xray)

        RuntimeDiagnostics.setDebugEnabled(this, repo.settings.debugModeEnabled)
        diagnostics.event(
            "APP",
            "repository-ready",
            "debugMode" to repo.settings.debugModeEnabled,
            "profiles" to repo.profiles.size
        )
    }
}
