package com.marbleng.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.quicktile.MarbleQuickTileService
import com.marbleng.app.ui.ConnectionPermissionDialog
import com.marbleng.app.ui.ConnectionPermissionStep
import com.marbleng.app.ui.MarbleApp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // MARBLE_CONNECT_PERMISSION_V12
    // MARBLE_CONNECT_CLICK_GUARD_V13
    // MARBLE_LIBRARY_IMPORT_TARGET_V33
    // MARBLE_SYSTEM_INTEGRITY_V38
    // MARBLE_CONTEXTUAL_CONNECTION_ACCESS_V103
    private companion object {
        /** Survives recreation while Android's VPN consent UI is open. */
        const val KEY_PENDING_PROFILE = "pendingProfileId"
        const val KEY_PENDING_PROFILE_SOURCE = "pendingProfileSourceId"
        const val KEY_PENDING_PERMISSION_STEP = "pendingPermissionStep"

        /** Untrusted SAF documents are bounded before parsing or Compose state mutation. */
        const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
    }

    private var pending: ProxyProfile? = null
    private var permissionQueue: List<ConnectionPermissionStep> = emptyList()
    private var permissionIndex = 0
    private var permissionError by mutableStateOf("")
    private var permissionStep by mutableStateOf<ConnectionPermissionStep?>(null)
    private val app get() = application as MarbleApplication

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        completePermissionStep(
            result.resultCode == Activity.RESULT_OK || vpnConsentGranted()
        )
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        completePermissionStep(granted || notificationsGranted())
    }

    private val batterySettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        completePermissionStep(batteryExemptionGranted())
    }

    private val openFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        // SAF providers may be remote/slow and may not expose Content-Length. Never read an
        // arbitrary document on Android's input thread and never allocate an unbounded String.
        lifecycleScope.launch {
            val text = runCatching { readImportText(uri) }.getOrElse { error ->
                app.repo.setRuntimeMessage(
                    "Import failed • ${error.message ?: error::class.java.simpleName}"
                )
                return@launch
            }
            app.repo.importText(
                text,
                "Imported file",
                // MARBLE_MANUAL_BUCKET_V122 — "all" is a view, not a bucket; land in Manual.
                app.repo.intakeTargetOrManual(app.repo.librarySourceFilter)
            )
        }
    }

    private fun vpnConsentGranted(): Boolean = VpnService.prepare(this) == null

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun batteryExemptionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val power = getSystemService(PowerManager::class.java)
        return power?.isIgnoringBatteryOptimizations(packageName) == true
    }

    /**
     * Permission order is deterministic so the explanation always precedes the Android prompt.
     * The first connection arms the complete protected-connection access set in one contextual
     * sequence, including Local Proxy mode; subsequent connections skip already-granted access.
     */
    private fun missingConnectionPermissions(): List<ConnectionPermissionStep> = buildList {
        if (!vpnConsentGranted()) add(ConnectionPermissionStep.VPN)
        if (!notificationsGranted()) add(ConnectionPermissionStep.NOTIFICATIONS)
        if (!batteryExemptionGranted()) add(ConnectionPermissionStep.BATTERY)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pending?.let {
            outState.putString(KEY_PENDING_PROFILE, it.id)
            outState.putString(KEY_PENDING_PROFILE_SOURCE, it.subscriptionId)
        }
        permissionStep?.let { outState.putString(KEY_PENDING_PERMISSION_STEP, it.name) }
    }

    // MARBLE_APP_UPDATE_FOREGROUND_V102
    override fun onStart() {
        super.onStart()
        app.repo.checkForAppUpdate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_PENDING_PROFILE)?.let { id ->
            val sourceId = savedInstanceState.getString(KEY_PENDING_PROFILE_SOURCE)
            pending = app.repo.profile(id, sourceId)
        }
        savedInstanceState?.getString(KEY_PENDING_PERMISSION_STEP)?.let { rawStep ->
            val restoredStep = runCatching { ConnectionPermissionStep.valueOf(rawStep) }.getOrNull()
            val profile = pending
            if (restoredStep != null && profile != null) {
                permissionQueue = missingConnectionPermissions().ifEmpty { listOf(restoredStep) }
                permissionIndex = permissionQueue.indexOf(restoredStep).coerceAtLeast(0)
                permissionStep = permissionQueue.getOrNull(permissionIndex)
            }
        }
        setContent {
            MarbleApp(
                repo = app.repo,
                onConnect = ::connect,
                onImportFile = {
                    openFile.launch(
                        arrayOf("text/*", "application/json", "application/octet-stream")
                    )
                }
            )
            permissionStep?.let { step ->
                ConnectionPermissionDialog(
                    step = step,
                    error = permissionError,
                    onContinue = ::requestCurrentPermission,
                    onDismiss = ::cancelConnectionPermission
                )
            }
        }
        handleQuickTileIntent(intent)
        restoreInterruptedTunnel()
    }

    /**
     * MARBLE_DURABLE_TUNNEL_INTENT_V133 — finish what the user asked for before the process died.
     *
     * Android kills this process to install an updated APK (`REASON_PACKAGE_UPDATE`, reason 16 in the
     * exit history) with no teardown callback, so a tunnel that was carrying traffic simply stopped.
     * The durable tunnel intent survives that kill and the last-route reference survives with it, so
     * the exact route can be restored instead of the user having to notice and reconnect by hand.
     *
     * It runs only when nothing else is already in flight: a pending connect, a permission prompt or
     * a live tunnel all take precedence.
     */
    private fun restoreInterruptedTunnel() {
        if (pending != null || permissionStep != null) return
        val repo = app.repo
        if (repo.state != "DISCONNECTED") return
        if (!repo.tunnelIntentActive()) return
        val route = repo.lastProfile()
        if (route == null) {
            repo.clearTunnelIntent("no-restorable-route")
            return
        }
        repo.setRuntimeMessage("Restoring the route the app update interrupted")
        connect(route)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickTileIntent(intent)
    }

    private fun handleQuickTileIntent(intent: Intent?) {
        if (intent?.action != MarbleQuickTileService.ACTION_CONNECT_LAST) return
        intent.action = Intent.ACTION_MAIN
        val last = app.repo.lastProfile()
        if (last == null) {
            app.repo.setRuntimeMessage("Choose and connect a server once to arm Quick Tile")
        } else {
            connect(last)
        }
    }

    /**
     * Connect is the only entry point for runtime access. If Android has not granted the required
     * access yet, pause here and show one contextual explanation per permission before continuing.
     */
    private fun connect(profile: ProxyProfile) {
        if (permissionStep != null) return

        val missing = missingConnectionPermissions()
        if (missing.isNotEmpty()) {
            pending = profile
            permissionQueue = missing
            permissionIndex = 0
            permissionError = ""
            permissionStep = permissionQueue.first()
            return
        }

        startConnectionNow(profile)
    }

    private fun requestCurrentPermission() {
        when (permissionStep) {
            ConnectionPermissionStep.VPN -> {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    completePermissionStep(true)
                } else {
                    runCatching { vpnPermission.launch(intent) }
                        .onFailure { error ->
                            permissionError = "Could not open Android VPN permission: " +
                                (error.message ?: error::class.java.simpleName)
                        }
                }
            }
            ConnectionPermissionStep.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    completePermissionStep(true)
                } else if (notificationsGranted()) {
                    completePermissionStep(true)
                } else {
                    runCatching {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }.onFailure { error ->
                        permissionError = "Could not open notification permission: " +
                            (error.message ?: error::class.java.simpleName)
                    }
                }
            }
            ConnectionPermissionStep.BATTERY -> {
                if (batteryExemptionGranted()) {
                    completePermissionStep(true)
                } else {
                    val direct = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    ).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    runCatching { batterySettings.launch(direct) }
                        .onFailure {
                            runCatching {
                                batterySettings.launch(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            }.onFailure { error ->
                                permissionError = "Could not open battery settings: " +
                                    (error.message ?: error::class.java.simpleName)
                            }
                        }
                }
            }
            null -> Unit
        }
    }

    private fun completePermissionStep(granted: Boolean) {
        if (!granted) {
            permissionError = when (permissionStep) {
                ConnectionPermissionStep.VPN -> "VPN access is required before MarbleNG can start a protected connection."
                ConnectionPermissionStep.NOTIFICATIONS -> "Notifications are required to keep the active tunnel visible."
                ConnectionPermissionStep.BATTERY -> "Background access is required to keep the tunnel stable when idle."
                null -> "Access was not granted."
            }
            return
        }

        permissionError = ""
        permissionIndex += 1
        val next = permissionQueue.getOrNull(permissionIndex)
        if (next != null) {
            permissionStep = next
            return
        }

        val selected = pending
        pending = null
        permissionQueue = emptyList()
        permissionIndex = 0
        permissionStep = null
        if (selected != null) startConnectionNow(selected)
    }

    private fun cancelConnectionPermission() {
        pending = null
        permissionQueue = emptyList()
        permissionIndex = 0
        permissionError = ""
        permissionStep = null
        app.repo.setRuntimeMessage("Connection cancelled • required access was not granted")
    }

    private fun startConnectionNow(p: ProxyProfile) {
        runCatching {
            when (app.repo.settings.connectionMode) {
                ConnectionMode.LOCAL_PROXY -> app.repo.startLocalProxy(p)
                ConnectionMode.FULL_TUN -> {
                    val prep = VpnService.prepare(this)
                    if (prep == null) {
                        app.repo.startVpn(p)
                    } else {
                        // A permission can be revoked between the preflight and dispatch. Re-open
                        // the same contextual step instead of silently failing the connection.
                        pending = p
                        permissionQueue = listOf(ConnectionPermissionStep.VPN)
                        permissionIndex = 0
                        permissionError = "VPN access changed before the connection could start."
                        permissionStep = ConnectionPermissionStep.VPN
                    }
                }
            }
        }.onFailure { error ->
            reportConnectFailure(
                "Connect request failed before service dispatch",
                error
            )
        }
    }

    private suspend fun readImportText(uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(uri)
            ?: error("Android could not open the selected file")

        input.use { stream ->
            val output = ByteArrayOutputStream(64 * 1024)
            val buffer = ByteArray(16 * 1024)
            var total = 0

            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue

                total += read
                require(total <= MAX_IMPORT_BYTES) {
                    "Import exceeds ${MAX_IMPORT_BYTES / 1024 / 1024} MiB"
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun reportConnectFailure(prefix: String, error: Throwable) {
        pending = null
        permissionQueue = emptyList()
        permissionIndex = 0
        permissionError = ""
        permissionStep = null
        app.repo.setRuntimeState("BLOCKED", prefix)
        app.repo.setRuntimeMessage(
            "$prefix • ${error::class.java.simpleName}: " +
                (error.message ?: "unknown error")
        )
    }
}
