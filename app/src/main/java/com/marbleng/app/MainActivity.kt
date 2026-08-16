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
                app.repo.startVpn(selected)
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
        when (app.repo.settings.connectionMode) {
            ConnectionMode.LOCAL_PROXY -> app.repo.startLocalProxy(p)
            ConnectionMode.FULL_TUN -> {
                val prep = VpnService.prepare(this)
                if (prep == null) {
                    app.repo.startVpn(p)
                } else {
                    pending = p
                    runCatching { vpnPermission.launch(prep) }
                        .onFailure { error ->
                            pending = null
                            app.repo.setRuntimeState(
                                "BLOCKED",
                                "Could not open Android VPN permission"
                            )
                            app.repo.setRuntimeMessage(
                                "Could not open Android VPN permission • ${error::class.java.simpleName}"
                            )
                        }
                }
            }
        }
    }
}
