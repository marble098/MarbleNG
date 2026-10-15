package com.marbleng.app.core

import android.content.Context
import android.net.ConnectivityDiagnosticsManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.util.concurrent.Executor

object ConnectivityDiagnosticsObserver {
    enum class Kind {
        DATA_STALL,
        CONNECTIVITY_REPORT,
        CONNECTIVITY_REPORTED
    }

    data class Signal(
        val kind: Kind,
        val network: Network,
        val hasConnectivity: Boolean? = null,
        val observedAtMs: Long = System.currentTimeMillis()
    )

    fun register(
        context: Context,
        executor: Executor,
        onSignal: (Signal) -> Unit
    ): Closeable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Closeable {}
        return Api30.register(context.applicationContext, executor, onSignal)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private object Api30 {
        fun register(
            context: Context,
            executor: Executor,
            onSignal: (Signal) -> Unit
        ): Closeable {
            val manager = context.getSystemService(ConnectivityDiagnosticsManager::class.java)
                ?: return Closeable {}
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val callback =
                object : ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback() {
                    override fun onDataStallSuspected(
                        report: ConnectivityDiagnosticsManager.DataStallReport
                    ) {
                        onSignal(Signal(Kind.DATA_STALL, report.network))
                    }

                    override fun onConnectivityReportAvailable(
                        report: ConnectivityDiagnosticsManager.ConnectivityReport
                    ) {
                        onSignal(Signal(Kind.CONNECTIVITY_REPORT, report.network))
                    }

                    override fun onNetworkConnectivityReported(
                        network: Network,
                        hasConnectivity: Boolean
                    ) {
                        onSignal(
                            Signal(
                                kind = Kind.CONNECTIVITY_REPORTED,
                                network = network,
                                hasConnectivity = hasConnectivity
                            )
                        )
                    }
                }
            manager.registerConnectivityDiagnosticsCallback(request, executor, callback)
            return Closeable {
                runCatching { manager.unregisterConnectivityDiagnosticsCallback(callback) }
            }
        }
    }
}
