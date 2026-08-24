package com.marbleng.app

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ProxyProfile
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
    private companion object {
        /** Survives recreation while Android's VPN consent UI is open. */
        const val KEY_PENDING_PROFILE = "pendingProfileId"
        const val KEY_PENDING_PROFILE_SOURCE = "pendingProfileSourceId"

        /** Untrusted SAF documents are bounded before parsing or Compose state mutation. */
        const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
    }

    private var pending: ProxyProfile? = null
    private val app get() = application as MarbleApplication

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val selected = pending
        pending = null

        if (result.resultCode == Activity.RESULT_OK) {
            if (selected != null) {
                runCatching {
                    app.repo.startVpn(selected)
                }.onFailure { error ->
                    reportConnectFailure(
                        "VPN permission returned but service dispatch failed",
                        error
                    )
                }
            } else {
                app.repo.setRuntimeState("DISCONNECTED", "")
                app.repo.setRuntimeMessage(
                    "VPN permission returned without a pending profile • tap Connect again"
                )
            }
        } else {
            app.repo.setRuntimeState("DISCONNECTED", "VPN permission denied")
            app.repo.setRuntimeMessage("VPN permission was not granted")
        }
    }

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
                app.repo.librarySourceFilter
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pending?.let {
            outState.putString(KEY_PENDING_PROFILE, it.id)
            outState.putString(KEY_PENDING_PROFILE_SOURCE, it.subscriptionId)
        }
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
        setContent {
            MarbleApp(app.repo, ::connect) {
                openFile.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
            }
        }
    }

    private fun connect(p: ProxyProfile) {
        runCatching {
            when (app.repo.settings.connectionMode) {
                ConnectionMode.LOCAL_PROXY -> app.repo.startLocalProxy(p)
                ConnectionMode.FULL_TUN -> {
                    val prep = VpnService.prepare(this)
                    if (prep == null) {
                        app.repo.startVpn(p)
                    } else {
                        pending = p
                        runCatching {
                            vpnPermission.launch(prep)
                        }.onFailure { error ->
                            reportConnectFailure(
                                "Could not open Android VPN permission",
                                error
                            )
                        }
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

    private fun reportConnectFailure(prefix: String, error: Throwable) {
        pending = null
        app.repo.setRuntimeState("BLOCKED", prefix)
        app.repo.setRuntimeMessage(
            "$prefix • ${error::class.java.simpleName}: " +
                (error.message ?: "unknown error")
        )
    }
}
