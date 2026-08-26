package com.marbleng.app.quicktile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.marbleng.app.MainActivity
import com.marbleng.app.MarbleApplication
import com.marbleng.app.model.ConnectionMode

/** A state-aware system tile which toggles the exact last successfully connected Library row. */
class MarbleQuickTileService : TileService() {
    private val app: MarbleApplication get() = application as MarbleApplication

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val repo = app.repo
            if (repo.state in setOf("CONNECTED", "CONNECTING", "BLOCKED")) {
                repo.stopVpn()
                updateTile("Disconnecting…")
                return@unlockAndRun
            }

            val profile = repo.lastProfile()
            if (profile == null) {
                repo.setRuntimeMessage("Choose and connect a Library node once to arm Quick Tile")
                openApp(connectLast = false)
                return@unlockAndRun
            }

            when (repo.settings.connectionMode) {
                ConnectionMode.LOCAL_PROXY -> repo.startLocalProxy(profile)
                ConnectionMode.FULL_TUN -> {
                    if (VpnService.prepare(this) == null) repo.startVpn(profile)
                    else openApp(connectLast = true)
                }
            }
            updateTile("Connecting…")
        }
    }

    private fun updateTile(transientSubtitle: String = "") {
        val tile = qsTile ?: return
        val repo = app.repo
        val last = repo.lastProfile()
        tile.state = when {
            repo.state == "CONNECTED" -> Tile.STATE_ACTIVE
            last == null -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = when (repo.state) {
            "CONNECTED" -> "MarbleNG • On"
            "CONNECTING" -> "MarbleNG • Connecting"
            "BLOCKED" -> "MarbleNG • Reset"
            else -> "MarbleNG"
        }
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = transientSubtitle.ifBlank {
                when {
                    repo.state == "CONNECTED" -> repo.stateDetail
                    last != null -> last.name
                    else -> "Connect once in app"
                }
            }.take(80)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            tile.contentDescription = when {
                repo.state == "CONNECTED" -> "Disconnect MarbleNG from ${repo.stateDetail}"
                last != null -> "Connect MarbleNG to ${last.name}"
                else -> "Open MarbleNG and choose a node"
            }
        }
        tile.updateTile()
    }

    private fun openApp(connectLast: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .setAction(if (connectLast) ACTION_CONNECT_LAST else Intent.ACTION_MAIN)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                if (connectLast) 4901 else 4900,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val ACTION_CONNECT_LAST = "com.marbleng.app.action.QUICK_TILE_CONNECT_LAST"

        fun requestRefresh(context: Context) {
            runCatching {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, MarbleQuickTileService::class.java)
                )
            }
        }
    }
}
