package com.marbleng.app

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.ui.MarbleApp

class MainActivity : ComponentActivity() {
    // MARBLE_CONNECT_PERMISSION_V12
    // MARBLE_CONNECT_CLICK_GUARD_V13
    private companion object {
        /** Survives the recreation that a rotation during the system VPN consent dialog causes. */
        const val KEY_PENDING_PROFILE = "pendingProfileId"
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
        uri?.let { u ->
            runCatching {
                contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { app.repo.importText(it, "Imported file") }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pending?.let { outState.putString(KEY_PENDING_PROFILE, it.id) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_PENDING_PROFILE)?.let { id ->
            pending = app.repo.profile(id)
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
