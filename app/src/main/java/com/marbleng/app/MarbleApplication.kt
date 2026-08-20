package com.marbleng.app

// MARBLE_ULTIMATE_DIAGNOSTICS_BOOT_V15
// MARBLE_MEMORY_PRESSURE_BOOT_V26

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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::repo.isInitialized) repo.onMemoryPressure(level)
    }

    @Deprecated("Compatibility callback for severe system memory pressure")
    override fun onLowMemory() {
        super.onLowMemory()
        if (::repo.isInitialized) repo.onMemoryPressure(100)
    }
}
